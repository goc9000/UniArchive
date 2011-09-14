/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import uniarchive.models.archive.IMService;


/**
 * A class for indexing objects in a manner that
 * allows quick lookup by service and name.
 * 
 * @param <T> The type of object to index
 */
public class NameIndex<T>
{
	protected Map<IMService,Map<String,T>> _map;
	
	/**
	 * Constructor.
	 */
	public NameIndex()
	{
		_map = new TreeMap<IMService,Map<String,T>>();
	}
	
	/**
	 * Checks whether the index is empty.
	 * 
	 * @return True or false
	 */
	public boolean isEmpty()
	{
		return _map.isEmpty();
	}
	
	/**
	 * Files a new item under a given service and name. If an item
	 * already exists at those coordinates, it is overwritten.
	 * 
	 * @param service The service under which the item is filed
	 * @param name The name under which the item is filed
	 * @param item The item to file
	 */
	public void addItem(IMService service, String name, T item)
	{
		if (!_map.containsKey(service)) _map.put(service, new TreeMap<String,T>());
		
		_map.get(service).put(name, item);
	}
	
	/**
	 * Removes an item from under a given service and name.
	 * 
	 * @param service The service under which the item is filed
	 * @param name The name under which the item is filed
	 * @return The removed item
	 */
	public T removeItem(IMService service, String name)
	{
		if (!this.itemExists(service, name)) return null;
		
		Map<String,T> subMap = _map.get(service);
		T item = subMap.get(name);
		
		subMap.remove(name);
		if (subMap.isEmpty()) _map.remove(service);
		
		return item;
	}
	
	/**
	 * Removes all filed items.
	 */
	public void clear()
	{
		this._map.clear();
	}
	
	/**
	 * Checks whether any item is filed under a given service and name.
	 * 
	 * @param service A service
	 * @param name A name
	 * @return True if an item is filed there, false otherwise
	 */
	public boolean itemExists(IMService service, String name)
	{
		return _map.containsKey(service) && _map.get(service).containsKey(name);
	}
	
	/**
	 * Retrieves an item from the index.
	 * 
	 * @param service The service under which the item is filed
	 * @param name The name under which the item is filed
	 * @param item The item filed at those coordinates, or null if
	 *             it does not exist.
	 */
	public T getItem(IMService service, String name)
	{
		if (!this.itemExists(service, name)) return null;
		
		return _map.get(service).get(name);
	}
	
	/**
	 * Gets a list of all the items filed.
	 * 
	 * @return A list containing the items
	 */
	public List<T> getAllItems()
	{
		List<T> list = new ArrayList<T>();
		
		for (Map<String,T> subMap : _map.values())
			list.addAll(subMap.values());
		
		return list;
	}
	
	/**
	 * Gets a list of all the items filed under
	 * a given service.
	 * 
	 * @return A list containing the items
	 */
	public List<T> getAllItems(IMService service)
	{
		if (!_map.containsKey(service)) return new ArrayList<T>();
		
		return new ArrayList<T>(_map.get(service).values());
	}
	
	/**
	 * Gets a list of all the names filed under
	 * a given service.
	 * 
	 * @return A list containing the names
	 */
	public List<String> getAllNames(IMService service)
	{
		if (!_map.containsKey(service)) return new ArrayList<String>();
		
		return new ArrayList<String>(_map.get(service).keySet());
	}
	
	@Override
	public String toString()
	{
		StringBuffer buff = new StringBuffer();
		
		for (IMService service : _map.keySet())
		{
			buff.append(service.shortName);
			buff.append(":\n");
			
			for (String name : _map.get(service).keySet())
			{
				Object item = _map.get(service).get(name);
				
				buff.append("\t");
				buff.append(name);
				buff.append(":");
				buff.append((item == null) ? "null" : item.toString());
				buff.append("\n");
			}
		}
		
		return buff.toString();
	}
}
