/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.gaim_import;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uniarchive.models.import_common.Feedback;

/**
 * This class represents a query from the import process
 * asking the user to confirm the list of accounts to
 * create, and their aliases.
 */
public class ConfirmAccountsQuery extends Feedback
{
	public final List<ImportedAccountInfo> accountInfo;
	
	public ConfirmAccountsQuery(List<ImportedAccountInfo> accountInfo)
	{
		this.accountInfo = new ArrayList<ImportedAccountInfo>(accountInfo);
		Collections.sort(this.accountInfo);
	}
}