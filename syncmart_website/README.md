# SyncMart Website

A modern, responsive web application that mirrors the functionality of the SyncMart Android app. Built with vanilla JavaScript, HTML5, and CSS3, featuring real-time collaboration, Google authentication, and Firebase backend integration.

## 🌟 Features

### Core Functionality
- **Google Authentication**: Secure sign-in with Google accounts
- **Shopping Lists Management**: Create, edit, and delete shopping lists
- **Real-time Collaboration**: Share lists with friends and see updates instantly
- **Item Management**: Add, mark as complete, and prioritize items
- **Friends System**: Add and manage friends for list sharing
- **WhatsApp Integration**: Share shopping lists via WhatsApp or clipboard

### User Interface
- **Responsive Design**: Works seamlessly on desktop, tablet, and mobile
- **Modern UI**: Beautiful gradient design matching the Android app
- **Real-time Updates**: Live synchronization across all devices
- **Offline Support**: Basic offline functionality with Firebase persistence
- **Keyboard Shortcuts**: Quick navigation and actions
- **Toast Notifications**: User-friendly feedback messages

### Technical Features
- **Progressive Web App**: Installable on mobile devices
- **Service Worker**: Offline caching and background sync
- **Firebase Integration**: Real-time database and authentication
- **Modern JavaScript**: ES6+ features and modular architecture
- **CSS Grid & Flexbox**: Modern layout techniques
- **Performance Optimized**: Fast loading and smooth interactions

## 🚀 Getting Started

### Prerequisites
- A Firebase project with Authentication and Firestore enabled
- Google Cloud Console access for OAuth configuration
- Modern web browser with JavaScript enabled

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd SyncMart/syncmart_website
   ```

2. **Configure Firebase**
   - Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Enable Google Authentication
   - Create a Firestore database
   - Get your Firebase configuration

3. **Update Firebase Configuration**
   - Open `js/config.js`
   - Replace the placeholder values with your actual Firebase project credentials:
   ```javascript
   const firebaseConfig = {
       apiKey: "YOUR_API_KEY",
       authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
       projectId: "YOUR_PROJECT_ID",
       storageBucket: "YOUR_PROJECT_ID.appspot.com",
       messagingSenderId: "YOUR_SENDER_ID",
       appId: "YOUR_APP_ID"
   };
   ```

4. **Deploy to Firebase Hosting**
   ```bash
   # Install Firebase CLI (if not already installed)
   npm install -g firebase-tools
   
   # Login to Firebase
   firebase login
   
   # Initialize Firebase (select hosting)
   firebase init hosting
   
   # Deploy to Firebase
   firebase deploy
   ```

5. **Configure Google OAuth**
   - In Firebase Console, go to Authentication > Sign-in method
   - Enable Google provider
   - Add your domain to authorized domains

## 📁 Project Structure

```
syncmart_website/
├── index.html              # Main HTML file
├── styles/                 # CSS stylesheets
│   ├── main.css           # Main styles and variables
│   ├── auth.css           # Authentication screen styles
│   ├── lists.css          # Lists and items styles
│   └── components.css     # Modal and component styles
├── js/                    # JavaScript modules
│   ├── config.js          # Firebase configuration
│   ├── auth.js            # Authentication management
│   ├── lists.js           # Shopping lists functionality
│   ├── friends.js         # Friends management
│   ├── ui.js              # UI interactions and modals
│   └── app.js             # Main app initialization
├── assets/                # Static assets
│   └── syncmart-logo.svg  # App logo
├── firebase.json          # Firebase hosting configuration
└── README.md              # This file
```

## 🎯 Usage

### For Users

1. **Sign In**: Click "Sign in with Google" to authenticate
2. **Create Lists**: Click the "+" button to create new shopping lists
3. **Add Items**: Open a list and click "+" to add items
4. **Share Lists**: Select friends when creating or editing lists
5. **Mark Items**: Click checkboxes to mark items as complete
6. **Share via WhatsApp**: Use the "Send WAPP" button to share lists

### For Developers

#### Adding New Features
1. Create new JavaScript modules in the `js/` directory
2. Add corresponding CSS styles in `styles/`
3. Update the main HTML file if needed
4. Register new modules in `js/app.js`

#### Styling Guidelines
- Use CSS custom properties (variables) for consistent theming
- Follow the existing color scheme and design patterns
- Ensure responsive design for all screen sizes
- Use modern CSS features like Grid and Flexbox

#### JavaScript Architecture
- Follow the modular pattern established in existing files
- Use ES6+ features and async/await for better readability
- Implement proper error handling and user feedback
- Maintain separation of concerns between UI and business logic

## 🔧 Configuration

### Firebase Security Rules
Set up Firestore security rules to protect your data:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own data
    match /Users/{userId} {
      allow read, write: if request.auth != null && request.auth.token.email == userId;
    }
    
    // Shopping lists: owner can read/write, shared users can read/write
    match /shopping_lists/{listId} {
      allow read, write: if request.auth != null && 
        (resource.data.owner == request.auth.token.email || 
         request.auth.token.email in resource.data.accessEmails);
    }
  }
}
```

### Environment Variables
For production deployment, consider using environment variables for sensitive configuration.

## 🚀 Deployment

### Firebase Hosting
The website is configured for Firebase Hosting deployment:

```bash
firebase deploy --only hosting
```

### Custom Domain
1. Add custom domain in Firebase Console
2. Update DNS records as instructed
3. Configure SSL certificate (automatic with Firebase)

### CDN Configuration
The Firebase configuration includes caching headers for optimal performance.

## 🔒 Security

- All user data is protected by Firebase Authentication
- Firestore security rules ensure data access control
- HTTPS is enforced for all connections
- Input validation and sanitization implemented
- XSS protection through proper DOM manipulation

## 📱 Progressive Web App

The website is configured as a Progressive Web App with:
- Web App Manifest for installation
- Service Worker for offline functionality
- Responsive design for all devices
- Fast loading and smooth interactions

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🆘 Support

For support and questions:
- Check the Firebase documentation
- Review the code comments for implementation details
- Open an issue in the repository

## 🔄 Updates

The website automatically syncs with the Android app through the shared Firebase backend, ensuring consistent data across all platforms.

---

**Built with ❤️ for seamless shopping list collaboration**
