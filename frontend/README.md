# Frontend

React + TypeScript + Vite console for AI Incident Copilot.

## Current Structure

- `src/App.tsx`: MVP console with incident list, workflow timeline, metrics, actions, tool calls, and postmortem.
- `src/styles.css`: Console styling.
- `src/main.tsx`: React entrypoint.

## Commands

```bash
npm ci
npm run build
npm run dev
```

## Future Structure

For a larger release, split the current MVP console into:

- `api/`: API client and DTO types.
- `components/`: reusable UI components.
- `features/incidents`
- `features/workflows`
- `features/actions`
- `features/reports`
