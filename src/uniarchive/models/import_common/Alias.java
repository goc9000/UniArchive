/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models.import_common;

import uniarchive.models.archive.FreeAccount;
import uniarchive.models.archive.IMService;

/**
 * Class for modeling a service-dependent alias,
 * resolved or otherwise.
 */
public class Alias implements Comparable<Alias>
{
	public IMService service;
	public String name;
	public FreeAccount resolution;
	
	/**
	 * Constructor.
	 * 
	 * @param service The IM service for which the alias is valid
	 * @param name A name
	 * @param resolution The account to which this alias resolves
	 *                   (null if is is unresolved)
	 */
	public Alias(IMService service, String name, FreeAccount resolution)
	{
		this.service = service;
		this.name = name;
		this.resolution = resolution;
	}
	
	/**
	 * Checks whether this alias is unresolved.
	 * 
	 * @return True or false
	 */
	public boolean isUnresolved()
	{
		return (this.resolution == null);
	}

	@Override
	public int compareTo(Alias other)
	{
		if (other.service != this.service) return this.service.compareTo(other.service);
		
		return this.name.compareTo(other.name);
	}
}
