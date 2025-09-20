// UI Management Module for SyncMart Website
// Handles modals, toasts, navigation, and user interface interactions

class UIManager {
    constructor() {
        this.currentModal = null;
        this.toastTimeout = null;
        
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.setupNavigation();
    }

    setupEventListeners() {
        // Modal close buttons
        document.querySelectorAll('.modal-close').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const modalId = e.target.closest('.modal').id;
                this.hideModal(modalId);
            });
        });

        // Modal overlay click
        document.getElementById('modalOverlay').addEventListener('click', (e) => {
            if (e.target.id === 'modalOverlay') {
                this.hideAllModals();
            }
        });

        // Tab switching
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                this.switchTab(e.target.dataset.tab);
            });
        });

        // Back buttons
        document.getElementById('backToListBtn')?.addEventListener('click', () => {
            window.SyncMart.lists.showListsSection();
        });

        document.getElementById('backFromFriendsBtn')?.addEventListener('click', () => {
            window.SyncMart.lists.showListsSection();
        });
    }

    setupNavigation() {
        // Menu button
        document.getElementById('menuBtn')?.addEventListener('click', () => {
            this.toggleNavigationDrawer();
        });

        // Navigation drawer items
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', (e) => {
                const action = e.currentTarget.dataset.action;
                this.handleNavigationAction(action);
            });
        });

        // Close drawer when clicking outside
        document.addEventListener('click', (e) => {
            const drawer = document.getElementById('navDrawer');
            const menuBtn = document.getElementById('menuBtn');
            
            if (drawer.classList.contains('open') && 
                !drawer.contains(e.target) && 
                !menuBtn.contains(e.target)) {
                this.closeNavigationDrawer();
            }
        });
    }

    // Modal Management
    showModal(modalId) {
        const modal = document.getElementById(modalId);
        const overlay = document.getElementById('modalOverlay');
        
        if (!modal) return;

        this.currentModal = modalId;
        modal.classList.remove('hidden');
        overlay.classList.remove('hidden');
        
        // Focus first input in modal
        const firstInput = modal.querySelector('input, textarea');
        if (firstInput) {
            setTimeout(() => firstInput.focus(), 100);
        }
    }

    hideModal(modalId) {
        const modal = document.getElementById(modalId);
        const overlay = document.getElementById('modalOverlay');
        
        if (!modal) return;

        modal.classList.add('hidden');
        
        // Hide overlay if no other modals are open
        const visibleModals = document.querySelectorAll('.modal:not(.hidden)');
        if (visibleModals.length === 0) {
            overlay.classList.add('hidden');
        }
        
        this.currentModal = null;
    }

    hideAllModals() {
        document.querySelectorAll('.modal').forEach(modal => {
            modal.classList.add('hidden');
        });
        document.getElementById('modalOverlay').classList.add('hidden');
        this.currentModal = null;
    }

    // List Modal
    showListModal(list = null) {
        const modal = document.getElementById('listModal');
        const title = document.getElementById('listModalTitle');
        const nameInput = document.getElementById('listNameInput');
        const saveBtn = document.getElementById('saveListBtn');
        const friendsSelection = document.getElementById('friendsSelection');

        if (list) {
            // Editing existing list
            title.textContent = 'Edit List';
            nameInput.value = list.listName;
            saveBtn.textContent = 'Update';
            
            // Populate friends selection
            window.SyncMart.friends.renderFriendsSelection(friendsSelection, list.accessEmails || []);
        } else {
            // Creating new list
            title.textContent = 'Create New List';
            nameInput.value = '';
            saveBtn.textContent = 'Create';
            
            // Populate friends selection
            window.SyncMart.friends.renderFriendsSelection(friendsSelection);
        }

        // Set up save button
        saveBtn.onclick = () => this.handleListSave(list);
        
        this.showModal('listModal');
    }

    async handleListSave(existingList) {
        const nameInput = document.getElementById('listNameInput');
        const friendsSelection = document.getElementById('friendsSelection');
        
        const name = nameInput.value.trim();
        if (!name) {
            this.showToast('Please enter a list name', 'error');
            return;
        }

        const selectedFriends = window.SyncMart.friends.getSelectedFriends(friendsSelection);

        try {
            if (existingList) {
                await window.SyncMart.lists.updateList(existingList.id, {
                    listName: name,
                    accessEmails: selectedFriends
                });
            } else {
                await window.SyncMart.lists.createList(name, selectedFriends);
            }
            
            this.hideModal('listModal');
        } catch (error) {
            console.error('Save list error:', error);
        }
    }

    // Items Modal
    showItemsModal() {
        const itemsInput = document.getElementById('itemsInput');
        const saveBtn = document.getElementById('addItemsSaveBtn');
        
        itemsInput.value = '';
        saveBtn.onclick = () => this.handleItemsSave();
        
        this.showModal('itemsModal');
    }

    async handleItemsSave() {
        const itemsInput = document.getElementById('itemsInput');
        const items = itemsInput.value.trim();
        
        if (!items) {
            this.showToast('Please enter some items', 'error');
            return;
        }

        const itemList = items.split('\n').map(item => item.trim()).filter(item => item.length > 0);
        
        if (itemList.length === 0) {
            this.showToast('Please enter at least one item', 'error');
            return;
        }

        const currentList = window.SyncMart.lists.getCurrentList();
        if (!currentList) {
            this.showToast('No list selected', 'error');
            return;
        }

        try {
            await window.SyncMart.lists.addItemsToList(currentList.id, itemList);
            this.hideModal('itemsModal');
        } catch (error) {
            console.error('Add items error:', error);
        }
    }

    // Friend Modal
    showFriendModal() {
        const emailInput = document.getElementById('friendEmailInput');
        const saveBtn = document.getElementById('addFriendSaveBtn');
        
        emailInput.value = '';
        saveBtn.onclick = () => this.handleFriendSave();
        
        this.showModal('friendModal');
    }

    async handleFriendSave() {
        const emailInput = document.getElementById('friendEmailInput');
        const email = emailInput.value.trim();
        
        if (!email) {
            this.showToast('Please enter an email address', 'error');
            return;
        }

        if (!this.isValidEmail(email)) {
            this.showToast('Please enter a valid email address', 'error');
            return;
        }

        try {
            await window.SyncMart.friends.addFriend(email);
            this.hideModal('friendModal');
        } catch (error) {
            console.error('Add friend error:', error);
        }
    }

    // Confirmation Modal
    showConfirmModal(title, message, onConfirm) {
        const titleElement = document.getElementById('confirmTitle');
        const messageElement = document.getElementById('confirmMessage');
        const confirmBtn = document.getElementById('confirmActionBtn');
        
        titleElement.textContent = title;
        messageElement.textContent = message;
        
        confirmBtn.onclick = () => {
            this.hideModal('confirmModal');
            if (onConfirm) onConfirm();
        };
        
        this.showModal('confirmModal');
    }

    // Toast Notifications
    showToast(message, type = 'info', duration = 5000) {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        // Clear existing timeout
        if (this.toastTimeout) {
            clearTimeout(this.toastTimeout);
        }

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        
        const icon = this.getToastIcon(type);
        
        toast.innerHTML = `
            <div class="toast-icon">${icon}</div>
            <div class="toast-message">${message}</div>
            <button class="toast-close" onclick="this.parentElement.remove()">
                <i class="fas fa-times"></i>
            </button>
        `;

        container.appendChild(toast);

        // Auto remove after duration
        this.toastTimeout = setTimeout(() => {
            if (toast.parentElement) {
                toast.remove();
            }
        }, duration);
    }

    getToastIcon(type) {
        switch (type) {
            case 'success':
                return '<i class="fas fa-check-circle"></i>';
            case 'error':
                return '<i class="fas fa-exclamation-circle"></i>';
            case 'warning':
                return '<i class="fas fa-exclamation-triangle"></i>';
            default:
                return '<i class="fas fa-info-circle"></i>';
        }
    }

    // Navigation
    toggleNavigationDrawer() {
        const drawer = document.getElementById('navDrawer');
        drawer.classList.toggle('open');
    }

    closeNavigationDrawer() {
        const drawer = document.getElementById('navDrawer');
        drawer.classList.remove('open');
    }

    handleNavigationAction(action) {
        this.closeNavigationDrawer();
        
        switch (action) {
            case 'friends':
                window.SyncMart.friends.showFriendsSection();
                break;
            case 'edit-profile':
                this.showEditProfileModal();
                break;
            case 'logout':
                this.confirmLogout();
                break;
            case 'delete-account':
                this.confirmDeleteAccount();
                break;
        }
    }

    // Tab Management
    switchTab(tabName) {
        // Update tab buttons
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');

        // Update content containers
        document.querySelectorAll('.items-container').forEach(container => {
            container.classList.remove('active');
        });
        document.getElementById(`${tabName}Items`).classList.add('active');
    }

    // Profile Management
    showEditProfileModal() {
        const user = window.SyncMart.auth.getCurrentUser();
        const userData = window.SyncMart.auth.getUserData();
        
        if (!user || !userData) {
            this.showToast('Unable to load profile data', 'error');
            return;
        }

        // For now, just show a simple message
        this.showToast('Profile editing feature coming soon!', 'info');
    }

    confirmLogout() {
        this.showConfirmModal(
            'Confirm Logout',
            'Are you sure you want to log out?',
            () => window.SyncMart.auth.signOut()
        );
    }

    confirmDeleteAccount() {
        this.showConfirmModal(
            'Delete Account',
            'Are you sure you want to delete your account? This action cannot be undone and will permanently delete all your data.',
            () => window.SyncMart.auth.deleteAccount()
        );
    }

    // Utility Methods
    isValidEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }

    // WhatsApp Integration
    async sendWhatsAppNotification() {
        const lists = window.SyncMart.lists.getLists();
        const pendingItems = [];
        
        lists.forEach(list => {
            const items = list.items || {};
            const listPendingItems = Object.values(items).filter(item => item.pending);
            if (listPendingItems.length > 0) {
                pendingItems.push({
                    listName: list.listName,
                    items: listPendingItems
                });
            }
        });

        if (pendingItems.length === 0) {
            this.showToast('No pending items to share!', 'warning');
            return;
        }

        const text = this.formatWhatsAppMessage(pendingItems);
        
        if (navigator.share) {
            try {
                await navigator.share({
                    title: 'SyncMart Shopping Lists',
                    text: text
                });
            } catch (error) {
                this.fallbackShare(text);
            }
        } else {
            this.fallbackShare(text);
        }
    }

    formatWhatsAppMessage(pendingItems) {
        let message = '🛒 *SyncMart Shopping Lists*\n\n';
        
        pendingItems.forEach(({ listName, items }) => {
            message += `*${listName}*\n`;
            items.forEach(item => {
                message += `• ${item.name}\n`;
            });
            message += '\n';
        });
        
        return message;
    }

    fallbackShare(text) {
        navigator.clipboard.writeText(text).then(() => {
            this.showToast('Shopping lists copied to clipboard!', 'success');
        }).catch(() => {
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            this.showToast('Shopping lists copied to clipboard!', 'success');
        });
    }
}

// Initialize UI Manager
window.SyncMart = window.SyncMart || {};
window.SyncMart.ui = new UIManager();
