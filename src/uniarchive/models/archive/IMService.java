/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.archive;

import java.util.Map;
import java.util.TreeMap;

import javax.swing.ImageIcon;

import uniarchive.graphics.IconManager;

/**
 * Enumeration class for modeling instant messaging services.
 */
public enum IMService
{
	GENERIC   ("Generic",       "generic"),
	YAHOO     ("Yahoo!",        "yahoo"),
	MSN       ("MSN Messenger", "msn"),
	GTALK     ("Google Talk",   "gtalk"),
	DIGSBY    ("Digsby",        "digsby");
	
	public final String friendlyName;
	public final String shortName;
	public final ImageIcon icon;
	
	protected static Map<String,IMService> _byShortName = null;
	
	/**
	 * Constructor.
	 * 
	 * @param friendlyName A "long" name for the service, for use in
	 *                     regular displays
	 * @param shortName A "short" identifier for the service, for use
	 *                  in debugging displays
	 */
	private IMService(String friendlyName, String shortName)
	{
		this.friendlyName = friendlyName;
		this.shortName = shortName;
		this.icon = IconManager.getInstance().getIcon("services/"+shortName+".png");
	}
	
	/**
	 * Returns the IMService having a given shortName, or null
	 * if none exists.
	 * 
	 * @param shortName
	 * @return
	 */
	public static IMService fromShortName(String shortName)
	{
		// Initialize map if it does not exist
		if (_byShortName == null)
		{
			_byShortName = new TreeMap<String,IMService>();
			
			for (IMService service : IMService.values())
				_byShortName.put(service.shortName, service);
		}
		
		return _byShortName.get(shortName);
	}
}
