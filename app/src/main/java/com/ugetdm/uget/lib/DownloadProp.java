/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class DownloadProp {
	public String    uri;
	public String    mirrors;
	public String    file;
	public String    folder;

	public String    user;
	public String    password;

	public String    referrer;
	public int       connections;
//	public int       retryLimit;

	public int       proxyType;
	public String    proxyHost;
	public int       proxyPort;
	public String    proxyUser;
	public String    proxyPassword;

	// Because old file doesn't save this field, I set a default value for old one.
	public int       group = Info.Group.queuing;    // UgetRelation.group
}
