// Lists Management Module for SyncMart Website
// Handles shopping lists creation, management, and real-time updates

class ListsManager {
    constructor() {
        this.db = window.SyncMart.config.db;
        this.lists = [];
        this.currentList = null;
        this.listeners = [];
        this.unsubscribers = [];
        
        this.init();
    }

    init() {
        // Set up real-time listeners when user is authenticated
        if (window.SyncMart.auth.isSignedIn()) {
            this.setupListeners();
        }
    }

    setupListeners() {
        const user = window.SyncMart.auth.getCurrentUser();
        if (!user) return;

        // Listen for lists where user is owner or has access
        const listsQuery = this.db.collection('shopping_lists')
            .where('owner', '==', user.email);

        const ownerUnsubscribe = listsQuery.onSnapshot((snapshot) => {
            this.handleListsUpdate(snapshot, 'owner');
        });

        // Listen for shared lists
        const sharedQuery = this.db.collection('shopping_lists')
            .where('accessEmails', 'array-contains', user.email);

        const sharedUnsubscribe = sharedQuery.onSnapshot((snapshot) => {
            this.handleListsUpdate(snapshot, 'shared');
        });

        this.unsubscribers.push(ownerUnsubscribe, sharedUnsubscribe);
    }

    handleListsUpdate(snapshot, type) {
        snapshot.docChanges().forEach((change) => {
            const listData = { id: change.doc.id, ...change.doc.data() };
            
            if (change.type === 'added' || change.type === 'modified') {
                this.updateList(listData, type);
            } else if (change.type === 'removed') {
                this.removeList(change.doc.id);
            }
        });

        this.renderLists();
    }

    updateList(listData, type) {
        const existingIndex = this.lists.findIndex(list => list.id === listData.id);
        
        if (existingIndex >= 0) {
            this.lists[existingIndex] = { ...this.lists[existingIndex], ...listData };
        } else {
            this.lists.push({ ...listData, type });
        }

        // Sort lists by position
        this.lists.sort((a, b) => (a.position || 0) - (b.position || 0));
    }

    removeList(listId) {
        this.lists = this.lists.filter(list => list.id !== listId);
        
        if (this.currentList && this.currentList.id === listId) {
            this.currentList = null;
            this.showListsSection();
        }
    }

    async createList(name, accessEmails = []) {
        try {
            const user = window.SyncMart.auth.getCurrentUser();
            if (!user) throw new Error('User not authenticated');

            const listData = {
                listName: name,
                owner: user.email,
                accessEmails: accessEmails,
                items: {},
                createdAt: firebase.firestore.FieldValue.serverTimestamp(),
                position: this.lists.length
            };

            const docRef = await this.db.collection('shopping_lists').add(listData);
            
            window.SyncMart.ui.showToast('List created successfully!', 'success');
            return docRef.id;
        } catch (error) {
            console.error('Create list error:', error);
            window.SyncMart.ui.showToast('Error creating list. Please try again.', 'error');
            throw error;
        }
    }

    async updateList(listId, updates) {
        try {
            await this.db.collection('shopping_lists').doc(listId).update({
                ...updates,
                lastUpdated: firebase.firestore.FieldValue.serverTimestamp()
            });
            
            window.SyncMart.ui.showToast('List updated successfully!', 'success');
        } catch (error) {
            console.error('Update list error:', error);
            window.SyncMart.ui.showToast('Error updating list. Please try again.', 'error');
            throw error;
        }
    }

    async deleteList(listId) {
        try {
            await this.db.collection('shopping_lists').doc(listId).delete();
            
            window.SyncMart.ui.showToast('List deleted successfully!', 'success');
        } catch (error) {
            console.error('Delete list error:', error);
            window.SyncMart.ui.showToast('Error deleting list. Please try again.', 'error');
            throw error;
        }
    }

    async addItemsToList(listId, items) {
        try {
            const user = window.SyncMart.auth.getCurrentUser();
            if (!user) throw new Error('User not authenticated');

            const userData = window.SyncMart.auth.getUserData();
            const userName = userData?.name || 'Unknown User';

            const newItems = {};
            items.forEach(itemName => {
                const itemId = this.db.collection('shopping_lists').doc().id;
                newItems[itemId] = {
                    name: itemName.trim(),
                    addedBy: userName,
                    addedAt: firebase.firestore.FieldValue.serverTimestamp(),
                    important: false,
                    pending: true
                };
            });

            await this.db.collection('shopping_lists').doc(listId).update({
                items: firebase.firestore.FieldValue.merge(newItems)
            });

            window.SyncMart.ui.showToast('Items added successfully!', 'success');
        } catch (error) {
            console.error('Add items error:', error);
            window.SyncMart.ui.showToast('Error adding items. Please try again.', 'error');
            throw error;
        }
    }

    async toggleItemStatus(listId, itemId, isPending) {
        try {
            const update = {};
            update[`items.${itemId}.pending`] = isPending;
            
            if (!isPending) {
                update[`items.${itemId}.completedAt`] = firebase.firestore.FieldValue.serverTimestamp();
            }

            await this.db.collection('shopping_lists').doc(listId).update(update);
        } catch (error) {
            console.error('Toggle item status error:', error);
            throw error;
        }
    }

    async toggleItemImportance(listId, itemId, isImportant) {
        try {
            await this.db.collection('shopping_lists').doc(listId).update({
                [`items.${itemId}.important`]: isImportant
            });
        } catch (error) {
            console.error('Toggle item importance error:', error);
            throw error;
        }
    }

    async deleteItem(listId, itemId) {
        try {
            const update = {};
            update[`items.${itemId}`] = firebase.firestore.FieldValue.delete();
            
            await this.db.collection('shopping_lists').doc(listId).update(update);
            window.SyncMart.ui.showToast('Item deleted successfully!', 'success');
        } catch (error) {
            console.error('Delete item error:', error);
            window.SyncMart.ui.showToast('Error deleting item. Please try again.', 'error');
            throw error;
        }
    }

    async updateListPositions(newPositions) {
        try {
            const batch = this.db.batch();
            
            newPositions.forEach((listId, index) => {
                const docRef = this.db.collection('shopping_lists').doc(listId);
                batch.update(docRef, { position: index });
            });

            await batch.commit();
        } catch (error) {
            console.error('Update positions error:', error);
            throw error;
        }
    }

    getListById(listId) {
        return this.lists.find(list => list.id === listId);
    }

    getLists() {
        return this.lists;
    }

    getCurrentList() {
        return this.currentList;
    }

    setCurrentList(list) {
        this.currentList = list;
        this.showListDetail();
    }

    clearLists() {
        this.lists = [];
        this.currentList = null;
        this.unsubscribers.forEach(unsubscribe => unsubscribe());
        this.unsubscribers = [];
    }

    renderLists() {
        const container = document.getElementById('listsContainer');
        const emptyState = document.getElementById('emptyState');
        
        if (!container) return;

        if (this.lists.length === 0) {
            container.innerHTML = '';
            emptyState.classList.remove('hidden');
            return;
        }

        emptyState.classList.add('hidden');
        
        container.innerHTML = this.lists.map(list => this.createListCard(list)).join('');
        
        // Add event listeners to list cards
        this.lists.forEach(list => {
            const card = document.querySelector(`[data-list-id="${list.id}"]`);
            if (card) {
                card.addEventListener('click', () => this.setCurrentList(list));
            }
        });
    }

    createListCard(list) {
        const items = list.items || {};
        const pendingCount = Object.values(items).filter(item => item.pending).length;
        const finishedCount = Object.values(items).filter(item => !item.pending).length;
        const importantCount = Object.values(items).filter(item => item.important).length;

        const isOwner = list.owner === window.SyncMart.auth.getCurrentUser()?.email;
        const ownerClass = isOwner ? 'owner' : '';
        const ownerText = isOwner ? 'You' : list.owner;

        return `
            <div class="list-card ${ownerClass}" data-list-id="${list.id}">
                <div class="list-card-header">
                    <div>
                        <div class="list-card-title">${list.listName}</div>
                        <div class="list-card-owner">Owner: ${ownerText}</div>
                    </div>
                    <div class="list-card-actions">
                        ${isOwner ? `
                            <button class="action-btn" onclick="event.stopPropagation(); window.SyncMart.lists.editList('${list.id}')">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button class="action-btn" onclick="event.stopPropagation(); window.SyncMart.lists.confirmDeleteList('${list.id}')">
                                <i class="fas fa-trash"></i>
                            </button>
                        ` : ''}
                    </div>
                </div>
                <div class="list-card-stats">
                    <div class="stat-item">
                        <i class="fas fa-clock"></i>
                        <span>${pendingCount} pending</span>
                    </div>
                    <div class="stat-item">
                        <i class="fas fa-check"></i>
                        <span>${finishedCount} finished</span>
                    </div>
                    ${importantCount > 0 ? `
                        <div class="stat-item">
                            <i class="fas fa-star" style="color: var(--warning-color);"></i>
                            <span>${importantCount} important</span>
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    showListsSection() {
        document.getElementById('listsSection').classList.remove('hidden');
        document.getElementById('listDetailSection').classList.add('hidden');
        document.getElementById('friendsSection').classList.add('hidden');
    }

    showListDetail() {
        document.getElementById('listsSection').classList.add('hidden');
        document.getElementById('listDetailSection').classList.remove('hidden');
        document.getElementById('friendsSection').classList.add('hidden');
        
        this.renderListDetail();
    }

    renderListDetail() {
        if (!this.currentList) return;

        const titleElement = document.getElementById('listDetailTitle');
        if (titleElement) {
            titleElement.textContent = this.currentList.listName;
        }

        this.renderItems();
        this.updateTabCounts();
    }

    renderItems() {
        if (!this.currentList) return;

        const items = this.currentList.items || {};
        const pendingContainer = document.getElementById('pendingItems');
        const finishedContainer = document.getElementById('finishedItems');

        const pendingItems = Object.entries(items).filter(([_, item]) => item.pending);
        const finishedItems = Object.entries(items).filter(([_, item]) => !item.pending);

        pendingContainer.innerHTML = pendingItems.map(([itemId, item]) => 
            this.createItemCard(itemId, item, true)
        ).join('');

        finishedContainer.innerHTML = finishedItems.map(([itemId, item]) => 
            this.createItemCard(itemId, item, false)
        ).join('');

        // Add event listeners to items
        this.addItemEventListeners();
    }

    createItemCard(itemId, item, isPending) {
        const importantClass = item.important ? 'important' : '';
        const finishedClass = !isPending ? 'finished' : '';
        const checkedClass = !isPending ? 'checked' : '';

        return `
            <div class="item-card ${importantClass} ${finishedClass}" data-item-id="${itemId}">
                <div class="item-checkbox ${checkedClass}" onclick="window.SyncMart.lists.toggleItem('${this.currentList.id}', '${itemId}')"></div>
                <div class="item-content">
                    <div class="item-name">${item.name}</div>
                    <div class="item-meta">Added by ${item.addedBy} on ${this.formatDate(item.addedAt)}</div>
                </div>
                <div class="item-actions">
                    <button class="item-btn important" onclick="window.SyncMart.lists.toggleImportance('${this.currentList.id}', '${itemId}')">
                        <i class="fas fa-star"></i>
                    </button>
                    <button class="item-btn delete" onclick="window.SyncMart.lists.deleteItem('${this.currentList.id}', '${itemId}')">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
        `;
    }

    addItemEventListeners() {
        // Add event listeners for item actions
        document.querySelectorAll('.item-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        });
    }

    updateTabCounts() {
        if (!this.currentList) return;

        const items = this.currentList.items || {};
        const pendingCount = Object.values(items).filter(item => item.pending).length;
        const finishedCount = Object.values(items).filter(item => !item.pending).length;

        const pendingCountElement = document.getElementById('pendingCount');
        const finishedCountElement = document.getElementById('finishedCount');

        if (pendingCountElement) pendingCountElement.textContent = pendingCount;
        if (finishedCountElement) finishedCountElement.textContent = finishedCount;
    }

    formatDate(timestamp) {
        if (!timestamp) return 'Unknown';
        
        const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
        return date.toLocaleDateString();
    }

    // Public methods for UI interactions
    async toggleItem(listId, itemId) {
        const list = this.getListById(listId);
        if (!list) return;

        const item = list.items[itemId];
        if (!item) return;

        await this.toggleItemStatus(listId, itemId, !item.pending);
    }

    async toggleImportance(listId, itemId) {
        const list = this.getListById(listId);
        if (!list) return;

        const item = list.items[itemId];
        if (!item) return;

        await this.toggleItemImportance(listId, itemId, !item.important);
    }

    async deleteItem(listId, itemId) {
        await this.deleteItem(listId, itemId);
    }

    editList(listId) {
        const list = this.getListById(listId);
        if (!list) return;

        window.SyncMart.ui.showListModal(list);
    }

    confirmDeleteList(listId) {
        window.SyncMart.ui.showConfirmModal(
            'Delete List',
            'Are you sure you want to delete this list? This action cannot be undone.',
            () => this.deleteList(listId)
        );
    }

    async shareList(listId) {
        const list = this.getListById(listId);
        if (!list) return;

        const items = list.items || {};
        const pendingItems = Object.values(items).filter(item => item.pending);
        
        if (pendingItems.length === 0) {
            window.SyncMart.ui.showToast('No pending items to share!', 'warning');
            return;
        }

        const text = `Shopping List: ${list.listName}\n\nPending items:\n${pendingItems.map(item => `• ${item.name}`).join('\n')}`;
        
        if (navigator.share) {
            try {
                await navigator.share({
                    title: list.listName,
                    text: text
                });
            } catch (error) {
                this.fallbackShare(text);
            }
        } else {
            this.fallbackShare(text);
        }
    }

    fallbackShare(text) {
        navigator.clipboard.writeText(text).then(() => {
            window.SyncMart.ui.showToast('List copied to clipboard!', 'success');
        }).catch(() => {
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            window.SyncMart.ui.showToast('List copied to clipboard!', 'success');
        });
    }
}

// Initialize Lists Manager
window.SyncMart = window.SyncMart || {};
window.SyncMart.lists = new ListsManager();
