package android.widget;

import android.test.Solo;

import android.widget.Button;

public class WeakButton {
	private Updater<Button> updater;

	public WeakButton(Button widget, Solo solo) {
		int id = widget.getId();
		updater = Updater.<Button>fromId(id, solo);
	}

	public WeakButton(Updater<Button> updater) {
		this.updater = updater;
	}

	public boolean isEnabled() {
		return updater.update().isEnabled();
	}
}