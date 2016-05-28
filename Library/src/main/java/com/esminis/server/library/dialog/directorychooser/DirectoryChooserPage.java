package com.esminis.server.library.dialog.directorychooser;

import android.os.Environment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.esminis.server.library.R;
import com.esminis.server.library.service.FileUtils;

import java.io.File;

class DirectoryChooserPage implements Page {

	private final DirectoryChooserAdapter adapter;
	private OnDirectoryChooserListener listener = null;
	private File parent;
	private final TextView viewError;
	private final TextView viewTitle;
	private final View buttonCreateDirectory;

	DirectoryChooserPage(final DirectoryChooser chooser, ViewGroup container) {
		final ViewGroup layout = (ViewGroup)LayoutInflater.from(chooser.getContext())
			.inflate(R.layout.view_directory_chooser_page, container);
		final ListView listView = (ListView) layout.findViewById(R.id.list);
		adapter = new DirectoryChooserAdapter(chooser.getContext());
		viewError = (TextView) layout.findViewById(R.id.error);
		viewTitle = (TextView) layout.findViewById(R.id.title);
		buttonCreateDirectory = layout.findViewById(R.id.button_create_directory);
		buttonCreateDirectory.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					chooser.showCreateDirectory(parent);
				}
			}
		);
		layout.findViewById(R.id.button_choose).setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (listener != null) {
						listener.OnDirectoryChosen(parent);
					}
					chooser.dismiss();
				}
			}
		);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(
				AdapterView<?> parent, View view, int position, long id
			) {
				setParent(adapter.getItem(position).file);
			}
		});
		listView.setAdapter(adapter);
		setParent(Environment.getExternalStorageDirectory());
	}

	@Override
	public void setParent(File parent) {
		this.parent = parent;
		if (parent != null) {
			viewTitle.setText(
				Html.fromHtml(
					viewTitle.getContext().getString(R.string.selected_directory, parent.getAbsolutePath())
				)
			);
		}
		adapter.setParent(parent);
		if (parent != null) {
			if (FileUtils.canWriteToDirectory(parent)) {
				viewError.setVisibility(View.GONE);
			} else {
				viewError.setVisibility(View.VISIBLE);
				viewError.setText(R.string.warning_selected_directory_not_writable);
			}
		}
		buttonCreateDirectory.setEnabled(FileUtils.canWriteToDirectory(parent));
	}

	@Override
	public void onShow() {
		setParent(parent);
	}

	void setOnDirectoryChooserListener(OnDirectoryChooserListener listener) {
		this.listener = listener;
	}

}
