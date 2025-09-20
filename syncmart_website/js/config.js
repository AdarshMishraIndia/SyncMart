// js/config.js
// Firebase Configuration for SyncMart Website

// Firebase configuration object
const firebaseConfig = {
    apiKey: "AIzaSyBXjJkiFeSQ-DJ8pK7qMXBMlvWJVk8OfG4",
    authDomain: "syncmart-2db9f.firebaseapp.com",
    projectId: "syncmart-2db9f",
    storageBucket: "syncmart-2db9f.appspot.com",
    messagingSenderId: "61170465939",
    appId: "1:61170465939:web:29626e28ee3a97262aa166"
};

// Initialize Firebase (only once)
if (!firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
}

// Initialize services
const auth = firebase.auth();
const db   = firebase.firestore();

// Firestore settings must be applied before use
db.settings({
    cacheSizeBytes: firebase.firestore.CACHE_SIZE_UNLIMITED
});

// Enable offline persistence
db.enablePersistence().catch((err) => {
    if (err.code === "failed-precondition") {
        console.log("Persistence failed - multiple tabs open");
    } else if (err.code === "unimplemented") {
        console.log("Persistence not supported");
    }
});

// Export for global access
window.SyncMart = window.SyncMart || {};
window.SyncMart.config = { auth, db, firebase };
