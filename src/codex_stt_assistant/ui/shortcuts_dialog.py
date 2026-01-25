from gi.repository import Gtk

from ..utils.shortcuts import SHORTCUTS


class ShortcutsDialog(Gtk.Window):
    def __init__(self, parent=None):
        super().__init__(title="Keyboard Shortcuts", transient_for=parent, modal=True)
        self.set_default_size(420, 300)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_top=12,
                      margin_bottom=12, margin_start=12, margin_end=12)

        for key, desc in SHORTCUTS:
            row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
            key_label = Gtk.Label(label=key)
            key_label.set_xalign(0.0)
            key_label.set_size_request(120, -1)
            desc_label = Gtk.Label(label=desc)
            desc_label.set_xalign(0.0)
            row.append(key_label)
            row.append(desc_label)
            box.append(row)

        close = Gtk.Button(label="Close")
        close.connect("clicked", lambda _btn: self.destroy())
        box.append(close)

        self.set_child(box)
        self.present()
