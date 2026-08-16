# Runtime Fix: Cannot read properties of undefined (reading 'filter')

## Root Cause
When refactoring the `AppContext.tsx` provider, the `simulators` array state was defined, populated, but inadvertently omitted from the `value` object that is returned by the context provider.
Because of this omission, any component calling `const { simulators } = useAppStore();` (such as `RegisterCompany.tsx` and `RecruitmentApply.tsx`) received `undefined`. This caused an `Uncaught TypeError` when these components subsequently attempted to call `simulators.filter()`.

## Fix Applied
I updated the `value` object returned in `AppContext.tsx` to include the `simulators` state array. The components now correctly receive an empty array on initialization (or the populated array from Firestore), preventing the `.filter()` crash.

## Verified
- Build passes successfully.
- The `simulators` state is correctly propagated to all subscribing components.
