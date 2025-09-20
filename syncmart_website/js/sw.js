// Service Worker for SyncMart Website
// Provides offline functionality and caching

const CACHE_NAME = 'syncmart-v1.0.1';
const urlsToCache = [
    '/',
    '/index.html',
    '/styles/main.css',
    '/styles/auth.css',
    '/styles/lists.css',
    '/styles/components.css',
    '/js/config.js',
    '/js/auth.js',
    '/js/lists.js',
    '/js/friends.js',
    '/js/ui.js',
    '/js/app.js',
    '/assets/syncmart-logo.svg'
];

// Install event - cache resources
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then((cache) => {
                console.log('Opened cache');
                return cache.addAll(urlsToCache);
            })
    );
});

// Fetch event - serve from cache when offline
self.addEventListener('fetch', (event) => {
    event.respondWith(
        caches.match(event.request)
            .then((response) => {
                // Return cached version or fetch from network
                if (response) {
                    return response;
                }
                return fetch(event.request);
            }
        )
    );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames.map((cacheName) => {
                    if (cacheName !== CACHE_NAME) {
                        console.log('Deleting old cache:', cacheName);
                        return caches.delete(cacheName);
                    }
                })
            );
        })
    );
});

// Background sync for offline actions
self.addEventListener('sync', (event) => {
    if (event.tag === 'background-sync') {
        event.waitUntil(doBackgroundSync());
    }
});

async function doBackgroundSync() {
    // Handle offline actions when connection is restored
    console.log('Background sync triggered');
    // This would handle any pending operations that were queued while offline
}
