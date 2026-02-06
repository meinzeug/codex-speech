
const backendPort = process.env.CODEX_BACKEND_PORT || process.env.PORT || "8000";

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
        }
    ]
};
