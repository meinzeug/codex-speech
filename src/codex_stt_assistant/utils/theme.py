from pathlib import Path

from gi.repository import Gtk


def apply_theme(theme_name, display=None):
    css_path = _resolve_css_path(theme_name)
    provider = Gtk.CssProvider()
    provider.load_from_path(str(css_path))
    if display is None:
        display = Gtk.Display.get_default()
    Gtk.StyleContext.add_provider_for_display(
        display,
        provider,
        Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION,
    )


def _resolve_css_path(theme_name):
    base_dir = Path(__file__).resolve().parent.parent / 'resources'
    theme = (theme_name or 'dark').lower()
    if theme == 'light':
        filename = 'style-light.css'
    else:
        filename = 'style-dark.css'
    return base_dir / filename
