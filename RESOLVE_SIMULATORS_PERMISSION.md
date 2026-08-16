# Runtime Fix: Simulators Permission Denied

## Root Cause
When the application started, it immediately established a Firestore snapshot subscription on the `simulators` collection. Since `firestore.rules` for the top-level collection defaults to `allow read: if isAuthenticated()`, and the user is not authenticated upon initial boot, this query threw a "Missing or insufficient permissions" error. 

## Fix Applied
I updated the `simulators` fetch in `src/context/AppContext.tsx` to depend on `currentUser?.id`. The `onSnapshot` listener is now only established after the user has successfully authenticated.

## Verified
- Build passes successfully.
- Simulators fetch without permission error.
