from gi.repository import Gtk


def show_error(parent, title, message, details=None):
    _show_dialog(parent, title, message, details)


def show_info(parent, title, message, details=None):
    _show_dialog(parent, title, message, details)


def _show_dialog(parent, title, message, details=None):
    dialog = Gtk.Dialog(title=title, transient_for=parent, modal=True)
    dialog.add_button("OK", Gtk.ResponseType.OK)
    dialog.connect("response", _on_dialog_response)

    content = dialog.get_content_area()
    box = Gtk.Box(
        orientation=Gtk.Orientation.VERTICAL,
        spacing=12,
        margin_top=12,
        margin_bottom=12,
        margin_start=12,
        margin_end=12,
    )

    label = Gtk.Label(label=message)
    label.set_wrap(True)
    label.set_xalign(0.0)
    box.append(label)

    if details:
        expander = Gtk.Expander(label="Details")
        textview = Gtk.TextView()
        textview.get_buffer().set_text(details)
        textview.set_editable(False)
        textview.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        expander.set_child(textview)
        box.append(expander)

    content.append(box)
    dialog.present()


def _on_dialog_response(dialog, _response):
    dialog.destroy()
