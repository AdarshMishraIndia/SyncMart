// js/auth.js
// Authentication Module for SyncMart Website

class AuthManager {
    constructor() {
        this.auth = window.SyncMart.config.auth;
        this.db   = window.SyncMart.config.db;
        this.currentUser = null;
        this.userData = null;

        this.init();
    }

    init() {
        this.auth.onAuthStateChanged((user) => {
            if (user) {
                this.currentUser = user;
                this.handleUserSignIn(user);
            } else {
                this.handleUserSignOut();
            }
        });
    }

    async signInWithGoogle() {
        try {
            const provider = new firebase.auth.GoogleAuthProvider();
            provider.addScope("email");
            provider.addScope("profile");

            const result = await this.auth.signInWithPopup(provider);
            return result;
        } catch (error) {
            console.error("Google sign-in error:", error);
            window.SyncMart.ui.showToast("Sign-in failed", "error");
            throw error;
        }
    }

    async handleUserSignIn(user) {
        try {
            const userRef = this.db.collection("Users").doc(user.email);
            const userDoc = await userRef.get();

            if (!userDoc.exists) {
                await this.createUserDocument(user);
            } else {
                this.userData = userDoc.data();
            }

            this.updateUIForSignedInUser(user);
            window.SyncMart.ui.showToast("Successfully signed in!", "success");
        } catch (error) {
            console.error("Error handling sign-in:", error);
            window.SyncMart.ui.showToast("Error signing in. Please try again.", "error");
        }
    }

    async createUserDocument(user) {
        const userData = {
            name: user.displayName || "Unknown User",
            email: user.email,
            friendsMap: {},
            createdAt: firebase.firestore.FieldValue.serverTimestamp(),
            lastLogin: firebase.firestore.FieldValue.serverTimestamp()
        };
        await this.db.collection("Users").doc(user.email).set(userData);
        this.userData = userData;
    }

    async signOut() {
        try {
            await this.auth.signOut();
            window.SyncMart.ui.showToast("Signed out successfully", "success");
        } catch (error) {
            console.error("Sign-out error:", error);
            window.SyncMart.ui.showToast("Error signing out", "error");
        }
    }

    async handleUserSignOut() {
        this.currentUser = null;
        this.userData = null;
        this.updateUIForSignedOutUser();
    }

    updateUIForSignedInUser(user) {
        document.getElementById("loadingScreen").classList.add("hidden");
        document.getElementById("authScreen").classList.add("hidden");
        document.getElementById("mainApp").classList.remove("hidden");

        const userNameElement = document.getElementById("userName");
        if (userNameElement) {
            userNameElement.textContent = `Welcome, ${user.displayName || "User"}`;
        }
    }

    updateUIForSignedOutUser() {
        document.getElementById("mainApp").classList.add("hidden");
        document.getElementById("authScreen").classList.remove("hidden");

        if (window.SyncMart.lists) window.SyncMart.lists.clearLists();
        if (window.SyncMart.friends) window.SyncMart.friends.clearFriends();
    }

    getCurrentUser() { return this.currentUser; }
    getUserData()    { return this.userData; }
    isSignedIn()     { return this.currentUser !== null; }
}

window.SyncMart = window.SyncMart || {};
window.SyncMart.auth = new AuthManager();

// Hook Google Sign-In button
document.addEventListener("DOMContentLoaded", () => {
    const btn = document.getElementById("googleSignInBtn");
    if (btn) {
        btn.addEventListener("click", () => window.SyncMart.auth.signInWithGoogle());
    }
});
