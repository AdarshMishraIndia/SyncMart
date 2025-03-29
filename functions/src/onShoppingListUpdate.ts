import * as functions from "firebase-functions";
import { db, messaging } from "./index";

// Define Firestore trigger for updates to shopping_lists/{listId}
export const onShoppingListUpdate = functions.firestore
  .document("shopping_lists/{listId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (!before || !after) {
      console.log("Document does not exist anymore.");
      return null;
    }

    if (JSON.stringify(before.pendingItems) === JSON.stringify(after.pendingItems)) {
      console.log("No change in pendingItems, skipping notification.");
      return null;
    }

    const listName: string = after.name || "Shopping List";
    const owner: string = after.owner;
    const accessEmails: string[] = after.accessEmails || [];

    const sender = (context.auth?.token as { email?: string })?.email || "Unknown";


    const recipients = new Set([owner, ...accessEmails]);
    recipients.delete(sender);

    if (recipients.size === 0) {
      console.log("No recipients left after excluding sender, skipping.");
      return null;
    }

    const tokens: string[] = [];

    await Promise.all([...recipients].map(async (email) => {
      const userDoc = await db.collection("Users").doc(email).get();
      if (userDoc.exists) {
        const userData = userDoc.data();
        if (userData?.tokens && Array.isArray(userData.tokens)) {
          tokens.push(...userData.tokens);
        }
      }
    }));

    if (tokens.length === 0) {
      console.log("No FCM tokens found for recipients, skipping.");
      return null;
    }

    const message = {
      tokens,
      notification: {
        title: "Shopping List Updated",
        body: `Pending items in "${listName}" were modified.`,
      },
    };

    try {
      const response = await messaging.sendEachForMulticast(message);

      console.log(`Notifications sent: ${response.successCount}`);
      return null;
    } catch (error) {
      console.error("Error sending notifications:", error);
      return null;
    }
  });
