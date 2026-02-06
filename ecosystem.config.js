
const backendPort = process.env.CODEX_BACKEND_PORT || "17500";
const settingsPort = process.env.CODEX_SETTINGS_PORT || "17000";

module.exports = {
    apps: [
        {
            name: "codex-backend",
            script: "./.venv/bin/uvicorn",
            args: `main:app --host 0.0.0.0 --port ${backendPort}`,
            cwd: "./apps/backend",
            interpreter: "none",
            env: {
                PYTHONPATH: ".",
                CODEX_BACKEND_PORT: backendPort,
            }
        },
        {
            name: "codex-web",
            script: "./.venv/bin/uvicorn",
            args: `main:app --host 0.0.0.0 --port ${settingsPort}`,
            cwd: "./apps/backend",
            interpreter: "none",
            env: {
                PYTHONPATH: ".",
                CODEX_SETTINGS_PORT: settingsPort,
            }
        }
    ]
};
