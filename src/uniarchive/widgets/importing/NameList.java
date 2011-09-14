/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets.importing;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

/**
 * Class for a name list widget used for the local name
 * selection query in the Gaim Import dialog.
 */
public class NameList extends JList
{
	private static final long serialVersionUID = 1L;
	
	protected static XferHandler _commonXferHandler = new XferHandler();
	
	/**
	 * Constructor.
	 */
	public NameList(String iconName)
	{
		super(new DefaultListModel());
		
		this.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		this.setDropMode(DropMode.INSERT);
		this.setDragEnabled(true);
		this.setTransferHandler(NameList._commonXferHandler);
	}
	
	/**
	 * Inserts a number of names into the list.
	 * 
	 * @param index The index where the first name will be inserted
	 * @param names A list of names to add
	 */
	public void insertNames(int index, Collection<String> names)
	{
		DefaultListModel model = (DefaultListModel)this.getModel();
		
		for (String name : names)
		{
			model.add(index, name);
			index++;
		}
	}
	
	/**
	 * Removes a number of names from the list.
	 * 
	 * @param names A list of names to remove
	 */
	public void removeNames(Collection<String> names)
	{
		DefaultListModel model = (DefaultListModel)this.getModel();
		
		for (String name : names) model.removeElement(name);
	}
	
	/**
	 * Replaces this list's contents with a specified
	 * list of names.
	 * 
	 * @param names A list of names
	 */
	public void setNames(Collection<String> names)
	{
		DefaultListModel model = (DefaultListModel)this.getModel();
		
		model.clear();
		this.insertNames(0, names);
	}
	
	/**
	 * Gets all names present in the control.
	 * 
	 * @return A list of names
	 */
	public List<String> getNames()
	{
		DefaultListModel model = (DefaultListModel)this.getModel();
		
		List<String> names = new ArrayList<String>();
		for (int i=0; i<model.getSize(); i++) names.add((String)model.getElementAt(i));
		
		return names;
	}
	
	/**
	 * Gets the currently selected names.
	 * 
	 * @return A list of names
	 */
	public List<String> getSelectedNames()
	{
		List<String> names = new ArrayList<String>();
		Object[] values = this.getSelectedValues();
		
		for (Object value : values) names.add((String)value);
		
		return names;
	}
	
	/**
	 * Class for a common transfer handler that enables drag-and-drop
	 * between NameList widgets.
	 */
	protected static class XferHandler extends TransferHandler
	{
		private static final long serialVersionUID = 1L;

		@Override
		public int getSourceActions(JComponent source)
		{
			return TransferHandler.MOVE;
		}
		
		@Override
		protected Transferable createTransferable(JComponent source)
		{
			return this._encodeNameList(((NameList)source).getSelectedNames());
        }
		
		@Override
		public void exportDone(JComponent source, Transferable data, int action)
		{
			List<String> names = this._decodeNameList(data);
			((NameList)source).removeNames(names);
		}

		@Override
		public boolean canImport(TransferHandler.TransferSupport dropInfo)
		{
            if (!dropInfo.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;

            JList.DropLocation dl = (JList.DropLocation)dropInfo.getDropLocation();
            if (dl.getIndex() == -1) return false;
            
            return true;
        }
		
		@Override
		public boolean importData(TransferHandler.TransferSupport dropInfo)
		{
            if (!dropInfo.isDrop() || !dropInfo.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            
            // Decode the imported data
            List<String> names = this._decodeNameList(dropInfo.getTransferable());
            
            // Add the names in the right place
            JList.DropLocation dl = (JList.DropLocation)dropInfo.getDropLocation();
            NameList destination = (NameList)dropInfo.getComponent();
            destination.insertNames(dl.getIndex(), names);
            
            return true;
        }
		
		/**
		 * Encodes a name-list transfer.
		 * 
		 * @param names A list of names
		 * @return A Transferable object that encapsulates the list of names
		 */
		protected Transferable _encodeNameList(List<String> names)
		{
			StringBuilder buffer = new StringBuilder();
            for (String name : names)
            {
            	if (buffer.length() > 0) buffer.append("\n");
            	buffer.append(name);
            }
            
            return new StringSelection(buffer.toString());
		}
		
		/**
		 * Decodes a name-list transfer.
		 * 
		 * @param data The Transferable object that encapsulates the list of names
		 * @return The list of names
		 */
		protected List<String> _decodeNameList(Transferable data)
		{
			List<String> names = new ArrayList<String>();
			
			try
            {
            	String[] strings = ((String)data.getTransferData(DataFlavor.stringFlavor)).split("\n");
            	for (String name : strings) names.add(name);
            }
			catch (Exception e)
            {
            }
            
            return names;
		}
	}
}