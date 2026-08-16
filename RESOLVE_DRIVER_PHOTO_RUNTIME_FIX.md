# Runtime Fix: Uncaught ReferenceError: driver is not defined

## Root Cause
When applying the `resolveDriverPhoto` function, a syntax mistake was introduced in `src/pages/admin/fleet/OperationsTab.tsx`:
```javascript
{job.resolveDriverPhoto(driver) || null ? ( ... )
```
Here, `job` does not have a `resolveDriverPhoto` method, and `driver` was not defined in the scope (it should have been `job.driver`). This caused a runtime `ReferenceError` that crashed the app when rendering the operations tab.

## Fix Applied
I replaced all instances of `job.resolveDriverPhoto(driver)` with `resolveDriverPhoto(job.driver)` in `OperationsTab.tsx`.

## Verified
- Build passes successfully.
- Driver photo resolution works safely without crashing the UI.
- All metrics, simulatorId, operations, and business rules remain strictly intact.
