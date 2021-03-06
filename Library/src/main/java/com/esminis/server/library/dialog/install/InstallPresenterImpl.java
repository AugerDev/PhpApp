/**
 * Copyright 2016 Tautvydas Andrikys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.esminis.server.library.dialog.install;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.esminis.server.library.R;
import com.esminis.server.library.activity.main.MainActivity;
import com.esminis.server.library.model.InstallPackage;
import com.esminis.server.library.model.manager.InstallPackageManager;
import com.esminis.server.library.service.background.BackgroundService;
import com.esminis.server.library.service.server.installpackage.InstallPackageTaskProvider;
import com.esminis.server.library.service.server.installpackage.InstallerPackage;

import org.json.JSONException;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

@Singleton
public class InstallPresenterImpl implements InstallPresenter {

	private InstallView view = null;
	private final InstallPackageManager manager;
	private InstallPackage installingPackage = null;
	private final PublishSubject<Void> subject = PublishSubject.create();
	private boolean installing = false;

	@Inject
	public InstallPresenterImpl(InstallPackageManager manager) {
		this.manager = manager;
	}

	@Override
	public void setView(InstallView view) {
		this.view = view;
	}

	@Override
	public void onCreate() {
		if (view != null) {
			view.setupOnCreate();
			if (installingPackage == null) {
				downloadList();
			} else {
				view.showMessageInstall(installingPackage, R.string.installing_package);
			}
		}
	}

	@Override
	public Observable<Void> show() {
		return subject.observeOn(AndroidSchedulers.mainThread());
	}

	@Override
	public void downloadList() {
		final InstallView view = this.view;
		if (view != null) {
			view.showMessage(true, R.string.downloading_packages);
			manager.get()
				.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(
				new Subscriber<InstallPackage[]>() {
					@Override
					public void onCompleted() {}

					@Override
					public void onError(Throwable e) {
						if (view == InstallPresenterImpl.this.view) {
							view.showMessageError(R.string.downloading_packages_failed, e);
						}
					}

					@Override
					public void onNext(InstallPackage[] list) {
						if (view == InstallPresenterImpl.this.view) {
							view.hideMessage();
							view.showList(list);
						}
					}
				}
			);
		}
	}

	@Override
	public void install(final InstallPackage model) {
		if (view != null) {
			installing = true;
			view.showMessageInstall(model, R.string.installing_package);
			installingPackage = model;
			final Bundle data = new Bundle();
			final Application application = view.getActivity().getApplication();
			final String action = MainActivity.getIntentActionInstallPackage(application);
			final BroadcastReceiver receiver = new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					if (view != null && action.equals(intent.getAction())) {
						final String progress = String
							.valueOf(Math.round(intent.getFloatExtra("progress", 0) * 100));
						switch (intent.getIntExtra("state", 0)) {
							case InstallerPackage.STATE_DOWNLOAD:
								view.showMessageInstall(model, R.string.installing_package_download, progress);
								break;
							case InstallerPackage.STATE_INSTALL:
								view.showMessageInstall(model, R.string.installing_package_files, progress);
								break;
						}
					}
				}
			};
			application.registerReceiver(receiver, new IntentFilter(action));
			try {
				data.putString("package", model.toJson().toString());
				BackgroundService.execute(
					application, InstallPackageTaskProvider.class,
					new Subscriber<Void>() {

						@Override
						public void onCompleted() {
							try {
								manager.setInstalled(model);
								installFinished(application, receiver);
								subject.onNext(null);
							} catch (JSONException e) {
								if (view != null) {
									view.showMessageInstallFailed(model, e);
								}
							}
						}

						@Override
						public void onError(Throwable e) {
							installFinished(application, receiver);
							if (view != null) {
								view.showMessageInstallFailed(model, e);
							}
						}

						@Override
						public void onNext(Void dummy) {}

					}, data
				);
			} catch (Throwable e) {
				installFinished(application, receiver);
				view.showMessageInstallFailed(model, e);
			}
		}
	}

	private void installFinished(Context context, BroadcastReceiver receiver) {
		installingPackage = null;
		installing = false;
		context.unregisterReceiver(receiver);
	}

	@Override
	public InstallPackage getInstalled() {
		return manager.getInstalled();
	}

	@Override
	public boolean isInstalling() {
		return installing;
	}

}
