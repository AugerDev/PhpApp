package com.esminis.server.mariadb.application;

import com.esminis.server.library.activity.main.MainPresenter;
import com.esminis.server.library.activity.main.MainPresenterImpl;
import com.esminis.server.library.application.LibraryApplication;
import com.esminis.server.library.model.manager.Log;
import com.esminis.server.library.model.manager.Network;
import com.esminis.server.library.preferences.Preferences;
import com.esminis.server.library.service.server.ServerControl;
import com.esminis.server.library.service.server.ServerNotification;
import com.esminis.server.library.service.server.installpackage.InstallerPackage;
import com.esminis.server.mariadb.server.InstallerPackageMariaDb;
import com.esminis.server.mariadb.server.MariaDb;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class MariaDbApplicationModule {

	private final LibraryApplication application;

	public MariaDbApplicationModule(LibraryApplication application) {
		this.application = application;
	}

	@Provides
	@Singleton
	public ServerControl provideServerControl(
		Network network, com.esminis.server.library.model.manager.Process process, Log log,
		Preferences preferences, ServerNotification serverNotification
	) {
		return new MariaDb(
			application, network, preferences, log, process, application.getIsMainApplicationProcess(),
			serverNotification
		);
	}

	@Provides
	@Singleton
	public InstallerPackage provideInstallerPackage(Preferences preferences, ServerControl control) {
		return new InstallerPackageMariaDb(preferences, control);
	}

	@Provides
	public MainPresenter provideMainPresenter(MainPresenterImpl implementation) {
		return implementation;
	}

}
