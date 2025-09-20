// Main App Initialization for SyncMart Website
// This file initializes the application and sets up all event listeners

class SyncMartApp {
    constructor() {
        this.isInitialized = false;
        this.init();
    }

    init() {
        // Wait for DOM to be ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => this.setupApp());
        } else {
            this.setupApp();
        }
    }

    setupApp() {
        if (this.isInitialized) return;
        
        this.setupEventListeners();
        this.setupKeyboardShortcuts();
        this.setupServiceWorker();
        this.setupOfflineDetection();
        
        this.isInitialized = true;
        console.log('SyncMart Web App initialized successfully!');
    }

    setupEventListeners() {
        // Google Sign-In Button
        document.getElementById('googleSignInBtn')?.addEventListener('click', async () => {
            try {
                await window.SyncMart.auth.signInWithGoogle();
            } catch (error) {
                console.error('Sign-in failed:', error);
            }
        });

        // Add List Button
        document.getElementById('addListBtn')?.addEventListener('click', () => {
            window.SyncMart.ui.showListModal();
        });

        // Add Items Button
        document.getElementById('addItemsBtn')?.addEventListener('click', () => {
            window.SyncMart.ui.showItemsModal();
        });

        // Share List Button
        document.getElementById('shareListBtn')?.addEventListener('click', () => {
            const currentList = window.SyncMart.lists.getCurrentList();
            if (currentList) {
                window.SyncMart.lists.shareList(currentList.id);
            }
        });

        // Add Friend Button
        document.getElementById('addFriendBtn')?.addEventListener('click', () => {
            window.SyncMart.ui.showFriendModal();
        });

        // WhatsApp Button
        document.getElementById('sendWhatsAppBtn')?.addEventListener('click', () => {
            window.SyncMart.ui.sendWhatsAppNotification();
        });

        // Modal close buttons with data-modal attribute
        document.querySelectorAll('[data-modal]').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const modalId = e.target.dataset.modal;
                window.SyncMart.ui.hideModal(modalId);
            });
        });

        // Enter key in modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) {
                const activeModal = document.querySelector('.modal:not(.hidden)');
                if (activeModal) {
                    const saveBtn = activeModal.querySelector('.btn-primary');
                    if (saveBtn) {
                        saveBtn.click();
                    }
                }
            }
        });

        // Escape key to close modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                window.SyncMart.ui.hideAllModals();
                window.SyncMart.ui.closeNavigationDrawer();
            }
        });

        // Prevent form submission on Enter in modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && e.target.matches('.modal input, .modal textarea')) {
                e.preventDefault();
            }
        });
    }

    setupKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Only trigger shortcuts when not typing in inputs
            if (e.target.matches('input, textarea')) return;

            switch (e.key) {
                case 'n':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        window.SyncMart.ui.showListModal();
                    }
                    break;
                case 'f':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        window.SyncMart.friends.showFriendsSection();
                    }
                    break;
                case 'h':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        window.SyncMart.lists.showListsSection();
                    }
                    break;
            }
        });
    }

    setupServiceWorker() {
        if ('serviceWorker' in navigator) {
            window.addEventListener('load', () => {
                navigator.serviceWorker.register('/sw.js')
                    .then(registration => {
                        console.log('ServiceWorker registration successful:', registration.scope);
                    })
                    .catch(error => {
                        console.log('ServiceWorker registration failed:', error);
                    });
            });
        }
    }

    setupOfflineDetection() {
        window.addEventListener('online', () => {
            window.SyncMart.ui.showToast('You are back online!', 'success');
        });

        window.addEventListener('offline', () => {
            window.SyncMart.ui.showToast('You are offline. Some features may be limited.', 'warning');
        });
    }

    // Utility methods
    showLoading() {
        document.getElementById('loadingScreen').classList.remove('hidden');
    }

    hideLoading() {
        document.getElementById('loadingScreen').classList.add('hidden');
    }

    // Error handling
    handleError(error, context = '') {
        console.error(`Error in ${context}:`, error);
        
        let message = 'An unexpected error occurred. Please try again.';
        
        if (error.code === 'auth/user-not-found') {
            message = 'User not found. Please check your credentials.';
        } else if (error.code === 'auth/wrong-password') {
            message = 'Incorrect password. Please try again.';
        } else if (error.code === 'auth/too-many-requests') {
            message = 'Too many failed attempts. Please try again later.';
        } else if (error.code === 'auth/network-request-failed') {
            message = 'Network error. Please check your connection.';
        } else if (error.message) {
            message = error.message;
        }
        
        window.SyncMart.ui.showToast(message, 'error');
    }

    // Performance monitoring
    logPerformance(label, startTime) {
        const endTime = performance.now();
        console.log(`${label}: ${endTime - startTime}ms`);
    }
}

// Initialize the app
const app = new SyncMartApp();

// Global error handler
window.addEventListener('error', (e) => {
    console.error('Global error:', e.error);
    app.handleError(e.error, 'Global');
});

// Unhandled promise rejection handler
window.addEventListener('unhandledrejection', (e) => {
    console.error('Unhandled promise rejection:', e.reason);
    app.handleError(e.reason, 'Promise');
});

// Export for debugging
window.SyncMartApp = app;
