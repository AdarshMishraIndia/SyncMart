# Deep Link Testing Guide

## Quick Start Testing

### Prerequisites
- SyncMart app installed on device/emulator
- Firebase project configured
- ADB installed and device connected

---

## Test 1: Direct Deep Link via ADB

### Test List Deep Link
```bash
# Replace YOUR_LIST_ID with an actual list ID from your Firestore
adb shell am start -W -a android.intent.action.VIEW -d "myapp://list/YOUR_LIST_ID" com.mana.syncmart
```

**Expected Result:** App opens and navigates directly to the specified shopping list.

### Test Dashboard Deep Link
```bash
adb shell am start -W -a android.intent.action.VIEW -d "myapp://dashboard" com.mana.syncmart
```

**Expected Result:** App opens on the dashboard (ListManagementActivity).

---

## Test 2: FCM Notification with Deep Link

### Send Test Notification from Firebase Console

1. Go to Firebase Console → Cloud Messaging
2. Click "Send your first message"
3. Fill in notification details:
   - **Title:** "New item added"
   - **Text:** "Check out your grocery list"
4. Click "Next" → Select your app
5. Click "Additional options"
6. Add custom data:
   - **Key:** `deepLink`
   - **Value:** `myapp://list/YOUR_LIST_ID`
7. Send the notification

**Expected Result:** Tapping the notification opens the specific list.

---

## Test 3: Programmatic FCM Test (Node.js)

### Using Firebase Admin SDK

```javascript
const admin = require('firebase-admin');

// Initialize Firebase Admin
admin.initializeApp({
  credential: admin.credential.cert('path/to/serviceAccountKey.json')
});

// Send notification with deep link to specific list
async function sendListNotification(fcmToken, listId) {
  const message = {
    notification: {
      title: 'New item added',
      body: 'John added milk to your list'
    },
    data: {
      deepLink: `myapp://list/${listId}`,
      type: 'NEW_ITEM_ADDED',
      list_id: listId
    },
    token: fcmToken
  };

  try {
    const response = await admin.messaging().send(message);
    console.log('Successfully sent message:', response);
  } catch (error) {
    console.log('Error sending message:', error);
  }
}

// Send notification with deep link to dashboard
async function sendDashboardNotification(fcmToken) {
  const message = {
    notification: {
      title: 'Welcome back!',
      body: 'Check your shopping lists'
    },
    data: {
      deepLink: 'myapp://dashboard'
    },
    token: fcmToken
  };

  try {
    const response = await admin.messaging().send(message);
    console.log('Successfully sent message:', response);
  } catch (error) {
    console.log('Error sending message:', error);
  }
}

// Usage
const userToken = 'USER_FCM_TOKEN_HERE';
const listId = 'YOUR_LIST_ID_HERE';

sendListNotification(userToken, listId);
// or
sendDashboardNotification(userToken);
```

---

## Test 4: Multiple Notifications

### Test Scenario
1. Send 3 different notifications with different list deep links
2. Don't tap any notification yet
3. Tap the first notification → Should open list 1
4. Go back to notification tray
5. Tap the second notification → Should open list 2
6. Go back to notification tray
7. Tap the third notification → Should open list 3

**Expected Result:** Each notification correctly navigates to its respective list.

---

## Test 5: App State Testing

### Test with App Closed
1. Force stop the app
2. Send notification with deep link
3. Tap notification

**Expected Result:** App launches and navigates to the target screen.

### Test with App in Background
1. Open the app
2. Press home button (app goes to background)
3. Send notification with deep link
4. Tap notification

**Expected Result:** App comes to foreground and navigates to the target screen.

### Test with App in Foreground
1. Open the app and stay on any screen
2. Send notification with deep link
3. Pull down notification tray and tap notification

**Expected Result:** App navigates to the target screen.

---

## Test 6: Edge Cases

### Invalid Deep Link
```bash
adb shell am start -W -a android.intent.action.VIEW -d "myapp://invalid/path" com.mana.syncmart
```

**Expected Result:** App opens on dashboard, logs warning in Logcat.

### Malformed Deep Link
```bash
adb shell am start -W -a android.intent.action.VIEW -d "http://list/abc123" com.mana.syncmart
```

**Expected Result:** Deep link is rejected (wrong scheme), app opens normally.

### Non-existent List ID
```bash
adb shell am start -W -a android.intent.action.VIEW -d "myapp://list/nonexistent123" com.mana.syncmart
```

**Expected Result:** App opens ListActivity, shows fallback UI (list not found).

---

## Monitoring & Debugging

### View Logs
```bash
# Filter for deep link related logs
adb logcat | grep -E "DeepLinkHelper|SyncMartMessaging|ListActivity"

# Or use Android Studio Logcat with filters:
# - Tag: DeepLinkHelper
# - Tag: SyncMartMessaging
# - Tag: ListActivity
```

### Expected Log Output (Success)
```
D/SyncMartMessaging: Extracted data - DeepLink: myapp://list/abc123
D/DeepLinkHelper: Navigating to list: abc123
D/ListActivity: Processing deep link URI: myapp://list/abc123
D/ListActivity: Extracted list ID from deep link: abc123
```

### Expected Log Output (Invalid Deep Link)
```
W/DeepLinkHelper: Invalid deep link scheme: http. Expected: myapp
```

---

## Verification Checklist

- [ ] Deep link to specific list works (app closed)
- [ ] Deep link to specific list works (app in background)
- [ ] Deep link to specific list works (app in foreground)
- [ ] Deep link to dashboard works
- [ ] Multiple notifications can be tapped in sequence
- [ ] Invalid deep links are handled gracefully
- [ ] Non-existent list IDs don't crash the app
- [ ] Logs show proper deep link processing
- [ ] Backward compatibility: old notifications still work
- [ ] Direct deep links via ADB work

---

## Common Issues & Solutions

### Issue: Notification doesn't navigate
**Solution:** Check if `deepLink` field is present in FCM data payload. Verify Logcat for errors.

### Issue: App crashes on deep link
**Solution:** Check Logcat for stack trace. Verify list ID exists in Firestore.

### Issue: Wrong list opens
**Solution:** Verify the `deepLink` value in FCM payload matches the expected format.

### Issue: Deep link doesn't work from external source
**Solution:** Verify `android:exported="true"` is set in AndroidManifest.xml for both activities.

---

## Firebase Cloud Functions Example

### Trigger notification on new item added

```javascript
const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.sendNewItemNotification = functions.firestore
  .document('shopping_lists/{listId}/items/{itemId}')
  .onCreate(async (snap, context) => {
    const listId = context.params.listId;
    const itemData = snap.data();
    
    // Get list details
    const listDoc = await admin.firestore()
      .collection('shopping_lists')
      .doc(listId)
      .get();
    
    const listName = listDoc.data().listName;
    const accessEmails = listDoc.data().accessEmails || [];
    
    // Get FCM tokens for all users with access
    const tokens = [];
    for (const email of accessEmails) {
      const userDoc = await admin.firestore()
        .collection('Users')
        .doc(email)
        .get();
      
      const userTokens = userDoc.data().fcmTokens || [];
      tokens.push(...userTokens);
    }
    
    // Send notification with deep link
    const message = {
      notification: {
        title: `New item in ${listName}`,
        body: `${itemData.addedBy} added ${itemData.name}`
      },
      data: {
        deepLink: `myapp://list/${listId}`,
        type: 'NEW_ITEM_ADDED',
        list_id: listId
      }
    };
    
    // Send to all tokens
    const promises = tokens.map(token => 
      admin.messaging().send({ ...message, token })
    );
    
    await Promise.all(promises);
    console.log(`Sent ${tokens.length} notifications for list ${listId}`);
  });
```

---

## Quick Reference

### Deep Link Formats
- List: `myapp://list/<listId>`
- Dashboard: `myapp://dashboard`

### FCM Data Field
```json
{
  "data": {
    "deepLink": "myapp://list/abc123"
  }
}
```

### ADB Command Template
```bash
adb shell am start -W -a android.intent.action.VIEW -d "DEEP_LINK_URL" com.mana.syncmart
```

---

**Happy Testing! 🚀**
