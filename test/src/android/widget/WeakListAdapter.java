package android.widget;

import android.test.Solo;

import android.widget.ListAdapter;

public class WeakListAdapter {
	private Updater<ListAdapter> updater;

	public WeakListAdapter(Updater<ListAdapter> updater) {
		this.updater = updater;
	}

	public int getCount() {
		return updater.update().getCount();
	}
}