from gi.repository import Gtk


class ModelDownloader(Gtk.Window):
    def __init__(self, parent=None):
        super().__init__(title="Model Downloader", transient_for=parent, modal=True)
        self.set_default_size(400, 200)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12, margin_top=12,
                      margin_bottom=12, margin_start=12, margin_end=12)
        label = Gtk.Label(label="Model download UI is not implemented yet.")
        label.set_wrap(True)
        label.set_xalign(0.0)
        box.append(label)

        close = Gtk.Button(label="Close")
        close.connect("clicked", lambda _btn: self.destroy())
        box.append(close)

        self.set_child(box)
        self.present()
