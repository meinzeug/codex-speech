import sys
import os
import argparse


def _init_gi():
    try:
        import gi
        gi.require_version('Gtk', '4.0')
        gi.require_version('Vte', '3.91')
        return gi
    except Exception as exc:
        print(f"Failed to initialize GTK/VTE: {exc}", file=sys.stderr)
        raise


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--no-terminal", action="store_true")
    args, gtk_args = parser.parse_known_args()

    if args.self_test:
        os.environ["CODEX_STT_TEST_MODE"] = "1"
    if args.no_terminal:
        os.environ["CODEX_STT_NO_TERMINAL"] = "1"

    _init_gi()
    from gi.repository import Gtk

    from .config import Config
    from .ui import MainWindow
    from .utils import configure_logging, install_excepthook
    from .ui.dialogs import show_error

    config = Config.load()
    configure_logging(config)

    def _error_callback(title, message, details):
        try:
            windows = Gtk.Window.list_toplevels()
        except Exception:
            windows = []
        if windows:
            show_error(windows[0], title, message, details)

    install_excepthook(error_callback=_error_callback)

    app = Gtk.Application(application_id="com.codex.stt.assistant")

    def on_activate(_app):
        window = MainWindow(app, config)
        window.present()
        if args.self_test:
            window.run_self_test(app)

    app.connect("activate", on_activate)
    return app.run([sys.argv[0]] + gtk_args)


if __name__ == '__main__':
    raise SystemExit(main())
