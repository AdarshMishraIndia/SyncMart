import * as admin from "firebase-admin";
import { onShoppingListUpdate } from "./onShoppingListUpdate";

// Initialize Firebase Admin SDK (only if not already initialized)
if (admin.apps.length === 0) {
    admin.initializeApp();
}

export const db = admin.firestore();
export const messaging = admin.messaging();

// Export Firestore trigger
export { onShoppingListUpdate };
