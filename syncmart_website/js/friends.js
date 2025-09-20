// Friends Management Module for SyncMart Website
// Handles friend management, adding/removing friends, and friend list display

class FriendsManager {
    constructor() {
        this.db = window.SyncMart.config.db;
        this.friends = [];
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

        // Listen for changes to user's friends list
        const userDocRef = this.db.collection('Users').doc(user.email);
        const unsubscribe = userDocRef.onSnapshot((doc) => {
            if (doc.exists) {
                const userData = doc.data();
                this.updateFriendsList(userData.friendsMap || {});
            }
        });

        this.unsubscribers.push(unsubscribe);
    }

    updateFriendsList(friendsMap) {
        this.friends = Object.entries(friendsMap).map(([email, name]) => ({
            email,
            name
        }));

        this.renderFriends();
    }

    async addFriend(friendEmail) {
        try {
            const user = window.SyncMart.auth.getCurrentUser();
            if (!user) throw new Error('User not authenticated');

            // Check if friend exists
            const friendDoc = await this.db.collection('Users').doc(friendEmail).get();
            if (!friendDoc.exists) {
                throw new Error('User not found. Please check the email address.');
            }

            const friendData = friendDoc.data();
            const userData = window.SyncMart.auth.getUserData();

            // Add friend to current user's friends list
            await this.db.collection('Users').doc(user.email).update({
                [`friendsMap.${friendEmail}`]: friendData.name
            });

            // Add current user to friend's friends list
            await this.db.collection('Users').doc(friendEmail).update({
                [`friendsMap.${user.email}`]: userData.name
            });

            window.SyncMart.ui.showToast('Friend added successfully!', 'success');
        } catch (error) {
            console.error('Add friend error:', error);
            window.SyncMart.ui.showToast(error.message || 'Error adding friend. Please try again.', 'error');
            throw error;
        }
    }

    async removeFriend(friendEmail) {
        try {
            const user = window.SyncMart.auth.getCurrentUser();
            if (!user) throw new Error('User not authenticated');

            // Remove friend from current user's friends list
            await this.db.collection('Users').doc(user.email).update({
                [`friendsMap.${friendEmail}`]: firebase.firestore.FieldValue.delete()
            });

            // Remove current user from friend's friends list
            await this.db.collection('Users').doc(friendEmail).update({
                [`friendsMap.${user.email}`]: firebase.firestore.FieldValue.delete()
            });

            window.SyncMart.ui.showToast('Friend removed successfully!', 'success');
        } catch (error) {
            console.error('Remove friend error:', error);
            window.SyncMart.ui.showToast('Error removing friend. Please try again.', 'error');
            throw error;
        }
    }

    getFriends() {
        return this.friends;
    }

    getFriendByEmail(email) {
        return this.friends.find(friend => friend.email === email);
    }

    clearFriends() {
        this.friends = [];
        this.unsubscribers.forEach(unsubscribe => unsubscribe());
        this.unsubscribers = [];
    }

    renderFriends() {
        const container = document.getElementById('friendsContainer');
        const emptyState = document.getElementById('friendsEmptyState');
        
        if (!container) return;

        if (this.friends.length === 0) {
            container.innerHTML = '';
            emptyState.classList.remove('hidden');
            return;
        }

        emptyState.classList.add('hidden');
        
        container.innerHTML = this.friends.map(friend => this.createFriendCard(friend)).join('');
        
        // Add event listeners to friend cards
        this.friends.forEach(friend => {
            const card = document.querySelector(`[data-friend-email="${friend.email}"]`);
            if (card) {
                const removeBtn = card.querySelector('.remove-friend-btn');
                if (removeBtn) {
                    removeBtn.addEventListener('click', () => this.confirmRemoveFriend(friend));
                }
            }
        });
    }

    createFriendCard(friend) {
        const initials = friend.name.split(' ').map(n => n[0]).join('').toUpperCase();
        
        return `
            <div class="friend-card" data-friend-email="${friend.email}">
                <div class="friend-info">
                    <div class="friend-avatar">${initials}</div>
                    <div class="friend-details">
                        <h3>${friend.name}</h3>
                        <p>${friend.email}</p>
                    </div>
                </div>
                <div class="friend-actions">
                    <button class="action-btn remove-friend-btn" title="Remove Friend">
                        <i class="fas fa-user-minus"></i>
                    </button>
                </div>
            </div>
        `;
    }

    showFriendsSection() {
        document.getElementById('listsSection').classList.add('hidden');
        document.getElementById('listDetailSection').classList.add('hidden');
        document.getElementById('friendsSection').classList.remove('hidden');
        
        this.renderFriends();
    }

    confirmRemoveFriend(friend) {
        window.SyncMart.ui.showConfirmModal(
            'Remove Friend',
            `Are you sure you want to remove ${friend.name} from your friends list?`,
            () => this.removeFriend(friend.email)
        );
    }

    // Method to get friends for list sharing
    getFriendsForSelection() {
        return this.friends.map(friend => ({
            email: friend.email,
            name: friend.name,
            selected: false
        }));
    }

    // Method to render friends selection in modals
    renderFriendsSelection(container, selectedEmails = []) {
        if (!container) return;

        if (this.friends.length === 0) {
            container.innerHTML = `
                <div class="friend-checkbox">
                    <div class="friend-checkbox-label">
                        <div class="friend-checkbox-name">No friends added yet</div>
                        <div class="friend-checkbox-email">Add friends to share lists with them</div>
                    </div>
                </div>
            `;
            return;
        }

        container.innerHTML = this.friends.map(friend => `
            <div class="friend-checkbox">
                <input type="checkbox" id="friend-${friend.email}" 
                       value="${friend.email}" 
                       ${selectedEmails.includes(friend.email) ? 'checked' : ''}>
                <label for="friend-${friend.email}" class="friend-checkbox-label">
                    <div class="friend-checkbox-name">${friend.name}</div>
                    <div class="friend-checkbox-email">${friend.email}</div>
                </label>
            </div>
        `).join('');
    }

    // Method to get selected friends from a friends selection container
    getSelectedFriends(container) {
        if (!container) return [];
        
        const checkboxes = container.querySelectorAll('input[type="checkbox"]:checked');
        return Array.from(checkboxes).map(checkbox => checkbox.value);
    }
}

// Initialize Friends Manager
window.SyncMart = window.SyncMart || {};
window.SyncMart.friends = new FriendsManager();
