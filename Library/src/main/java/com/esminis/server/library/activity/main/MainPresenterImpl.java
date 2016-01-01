package com.esminis.server.library.activity.main;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.esminis.server.library.EventMessage;
import com.esminis.server.library.R;
import com.esminis.server.library.application.LibraryApplication;
import com.esminis.server.library.model.manager.Log;
import com.esminis.server.library.model.manager.Network;
import com.esminis.server.library.permission.PermissionActivityHelper;
import com.esminis.server.library.permission.PermissionListener;
import com.esminis.server.library.preferences.Preferences;
import com.esminis.server.library.service.background.BackgroundService;
import com.esminis.server.library.service.server.ServerNotification;
import com.esminis.server.library.service.server.install.InstallServer;
import com.esminis.server.library.service.server.install.OnInstallServerListener;
import com.esminis.server.library.service.server.tasks.RestartIfRunningServerTaskProvider;
import com.esminis.server.library.service.server.tasks.ServerTaskProvider;
import com.esminis.server.library.service.server.tasks.StartServerTaskProvider;
import com.esminis.server.library.service.server.tasks.StatusServerTaskProvider;
import com.esminis.server.library.service.server.tasks.StopServerTaskProvider;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.File;

import javax.inject.Inject;

public class MainPresenterImpl implements MainPresenter {

	@Inject
	protected PermissionActivityHelper permissionHelper;

	@Inject
	protected InstallServer installServer;

	@Inject
	protected Network network;

	@Inject
	protected Log log;

	@Inject
	protected ServerNotification serverNotification;

	@Inject
	protected Bus bus;

	@Inject
	protected Preferences preferences;

	private final ReceiverManager receiverManager = new ReceiverManager();

	private MainView view = null;
	private Throwable installError = null;
	private boolean showInstallFinishedOnResume = false;
	private boolean paused = false;
	protected AppCompatActivity activity = null;

	static private final String KEY_ERROR = "errors";

	@Inject
	public MainPresenterImpl() {}

	@Override
	public void onDestroy() {
		if (view != null) {
			stop();
			view = null;
		}
		receiverManager.cleanup();
		permissionHelper.onDestroy();
	}

	@Override
	public void onCreate(AppCompatActivity activity, Bundle savedInstanceState, MainView view) {
		this.view = view;
		permissionHelper.onResume(this.activity = activity);
		final LibraryApplication application = (LibraryApplication)activity.getApplication();
		if (savedInstanceState != null) {
			view.setLog(savedInstanceState.getCharSequence(KEY_ERROR));
		}
		view.setMessage(
			true, false, true,
			activity.getString(R.string.permission_files_needed, activity.getString(R.string.title))
		);
		if (savedInstanceState == null) {
			try {
				activity.getFragmentManager().beginTransaction()
					.replace(R.id.drawer, application.getComponent().getDrawerFragment()).commit();
			} catch (Exception ignored) {}
		}
		requestPermission();
	}

	@Override
	public void onResume() {
		paused = false;
		bus.register(this);
		permissionHelper.onResume(activity);
		if (showInstallFinishedOnResume) {
			showInstallFinishedOnResume = false;
			showInstallFinished(activity);
		}
		receiverManager.onResume(activity);
		resetNetwork();
		serverStatus();
		resetLog();
	}

	@Override
	public void onPause() {
		paused = true;
		bus.unregister(this);
		permissionHelper.onPause();
		receiverManager.onPause();
	}

	@Override
	public void stop() {
		if (view != null) {
			view.closeDialog();
		}
	}

	@Override
	public void onPostCreate() {
		if (view != null) {
			view.syncDrawer();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (view != null) {
			final CharSequence log = view.getLog();
			if (log != null) {
				outState.putCharSequence(KEY_ERROR, log);
			}
		}
	}

	@Override
	public void requestPermission() {
		permissionHelper.request(
			Manifest.permission.WRITE_EXTERNAL_STORAGE, new PermissionListener() {

				@Override
				public void onGranted() {
					view.setMessage(true, true, false, activity.getString(R.string.server_installing));
					installServer.install(activity, new OnInstallServerListener() {
						@Override
						public void OnInstallNewVersionRequest(InstallServer installer) {
							if (view != null) {
								view.showInstallNewVersionRequest(getMessageNewVersion(activity));
							}
						}

						@Override
						public void OnInstallEnd(Throwable error) {
							installError = error;
							if (paused) {
								showInstallFinishedOnResume = true;
							} else {
								showInstallFinished(activity);
							}
						}
					});
				}

				@Override
				public void onDenied() {
				}

			}
		);
	}

	private void showInstallFinished(Context context) {
		if (view == null) {
			return;
		}
		if (installError != null) {
			view.setMessage(
				true, false, false,
				context.getString(R.string.server_installation_failed, installError.getMessage())
			);
			return;
		}
		view.showMainContent();
		view.setDocumentRoot(getRootDirectory(activity));
		view.setPort(getPort(activity), true);
		receiverManager.add(
			context, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION), new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					resetNetwork();
				}
			}
		);
		receiverManager.add(
			context, new IntentFilter(MainActivity.getIntentAction(context)), new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					if (view != null && MainActivity.getIntentAction(context).equals(intent.getAction())) {
						Bundle extras = intent.getExtras();
						if (extras != null && extras.containsKey("errorLine")) {
							resetLog();
						} else {
							if (extras != null && extras.getBoolean("running")) {
								view.showButton(MainView.BUTTON_STOP);
								final CharSequence title = Html.fromHtml(
									getServerRunningLabel(activity, extras.getString("address"))
								);
								view.setStatusLabel(title);
								serverNotification.show(
									activity, title.toString(), activity.getString(R.string.server_running_public)
								);
							} else {
								view.showButton(MainView.BUTTON_START);
								view.setStatusLabel(activity.getString(R.string.server_stopped));
								serverNotification.hide(activity);
							}
						}
					}
				}

			}
		);
		resetNetwork();
		serverStatus();
	}

	protected String getServerRunningLabel(Context context, String address) {
		return String.format(
			context.getString(R.string.server_running),
			"<a href=\"http://" + address + "\">" + address + "</a>"
		);
	}

	@Override
	public void requestPermissionsResult(int requestCode, @NonNull int[] grantResults) {
		permissionHelper.onRequestPermissionsResult(requestCode, grantResults);
	}

	@Override
	public void serverStart() {
		log.clear(activity);
		serverTask(StartServerTaskProvider.class);
		resetLog();
	}

	@Override
	public void serverStop() {
		serverTask(StopServerTaskProvider.class);
		resetLog();
	}

	@Override
	public void showAbout() {
		view.showAbout();
	}

	@Override
	public void showDocumentRootChooser() {
		view.showDocumentRootChooser(new File(getRootDirectory(activity)));
	}

	@Override
	public void onDocumentRootChosen(File documentRoot) {
		setRootDirectory(activity, documentRoot.getAbsolutePath());
		view.setDocumentRoot(getRootDirectory(activity));
		serverRestartIfRunning();
	}

	@Override
	public void portModified(String newValue) {
		String portPreference = getPort(activity);
		if (portPreference == null || portPreference.isEmpty()) {
			portPreference = activity.getString(R.string.default_port);
		}
		int port = Integer.parseInt(portPreference);
		try {
			port = Integer.parseInt(newValue);
		} catch (NumberFormatException ignored) {}
		if (port >= 1024 && port <= 65535) {
			setPort(activity, String.valueOf(port));
			serverRestartIfRunning();
			view.setPort(String.valueOf(port), true);
		} else {
			view.setPort(newValue, false);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(MenuInflater inflater, Menu menu) {
		return view != null && view.createMenu(inflater, menu);
	}

	@Override
	public boolean onMenuItemSelected(MenuItem item) {
		return view != null && view.onMenuItemSelected(item);
	}

	@Override
	public void onInstallNewVersionResponse(boolean confirmed) {
		if (confirmed) {
			installServer.installNewVersionConfirmed();
		} else {
			installServer.installFinish();
		}
	}

	@Override
	public void onServerInterfaceChanged(int position) {
		final String value = getAddress(activity);
		final String newValue = network.get(position).name;
		if (!value.equals(newValue)) {
			setAddress(activity, newValue);
			serverRestartIfRunning();
		}
	}

	private void serverRestartIfRunning() {
		serverTask(RestartIfRunningServerTaskProvider.class);
	}

	private void serverStatus() {
		serverTask(StatusServerTaskProvider.class);
	}

	private void serverTask(Class<? extends ServerTaskProvider> taskClass) {
		BackgroundService.execute(activity.getApplication(), taskClass);
	}

	private void resetLog() {
		if (view != null) {
			view.setLog(log.get(activity));
		}
	}

	private void resetNetwork() {
		if (view != null) {
			boolean changed = network.refresh();
			view.setServerInterfaces(
				network.get(), network.getPosition(getAddress(activity))
			);
			if (changed) {
				serverRestartIfRunning();
			}
		}
	}

	private String getPort(Context context) {
		return preferences.getString(context, Preferences.PORT);
	}

	private void setPort(Context context, String port) {
		preferences.set(context, Preferences.PORT, port);
	}

	private String getAddress(Context context) {
		return preferences.getString(context, Preferences.ADDRESS);
	}

	private void setAddress(Context context, String address) {
		preferences.set(context, Preferences.ADDRESS, address);
	}

	private String getRootDirectory(Context context) {
		return preferences.getString(context, Preferences.DOCUMENT_ROOT);
	}

	private void setRootDirectory(Context context, String root) {
		preferences.set(context, Preferences.DOCUMENT_ROOT, root);
	}

	private String getMessageNewVersion(Context context) {
		return context.getString(
			R.string.server_install_new_version_question, preferences.getBuild(context)
		);
	}

	@Subscribe
	public void onEventMessage(EventMessage event) {
		View view = activity.findViewById(R.id.container);
		if (view == null) {
			return;
		}
		final Snackbar snackbar = Snackbar.make(view, event.message, Snackbar.LENGTH_LONG);
		snackbar.setAction(R.string.dismiss, new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				snackbar.dismiss();
			}
		});
		snackbar.setActionTextColor(
			ContextCompat.getColor(view.getContext(), event.error ? R.color.error : R.color.main)
		);
		snackbar.show();
	}

}