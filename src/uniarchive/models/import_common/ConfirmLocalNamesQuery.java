/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.import_common;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class represents a query from the import process
 * asking the user to confirm the list of heuristically
 * determined local names.
 */
public class ConfirmLocalNamesQuery extends Feedback
{
	public final Set<String> localNames;
	public final Set<String> remoteNames;
	
	public ConfirmLocalNamesQuery(Set<String> localNames, Set<String> remoteNames)
	{
		this.localNames = new TreeSet<String>(localNames);
		this.remoteNames = new TreeSet<String>(remoteNames);
	}
	
	public ConfirmLocalNamesQuery applyAnswer(List<String> newLocalNames)
	{
		Set<String> newRemoteNames = new TreeSet<String>(this.localNames);
		newRemoteNames.addAll(this.remoteNames);
		newRemoteNames.removeAll(newLocalNames);
		
		return new ConfirmLocalNamesQuery(new TreeSet<String>(newLocalNames), newRemoteNames);
	}
}
