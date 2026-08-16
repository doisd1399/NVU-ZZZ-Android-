# Final simulator migration

Applied changes:
- simulatorId prioritized as canonical identifier.
- RecruitmentApply selector uses simulator records and stores id.
- Legacy simulator fields preserved for compatibility.
- Simulator member fallback now prefers simulatorId.
- Metrics filtering prefers simulatorId.

Manual validation still required in the running environment for Firebase data and UI flows.
