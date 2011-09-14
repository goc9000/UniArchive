/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Component;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;

import uniarchive.graphics.IconManager;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Group;

/**
 * Class for a combobox that works with archive items
 * (groups, contacts, accounts, etc.)
 */
public class ArchiveItemsCombo extends JComboBox
{
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor.
	 */
	public ArchiveItemsCombo()
	{
		super(new DefaultComboBoxModel());
		
		this.setRenderer(new Renderer());
	}
	
	/**
	 * Sets the list of items available in this combo
	 * box.
	 * 
	 * @param items A list of archive items
	 */
	public void setItems(List<?> items)
	{
		DefaultComboBoxModel model = (DefaultComboBoxModel)this.getModel();
		
		model.removeAllElements();
		for (Object item : items) model.addElement(item);
	}
	
	/**
	 * Internal class for a renderer for this combobox.
	 */
	protected static class Renderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		protected ImageIcon _groupIcon = IconManager.getInstance().getIcon("group");
		protected ImageIcon _contactIcon = IconManager.getInstance().getIcon("contact");
		protected ImageIcon _identityIcon = IconManager.getInstance().getIcon("identity");
		protected ImageIcon _identitiesIcon = IconManager.getInstance().getIcon("identities");
		
		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
		{
			String text = null;
			ImageIcon icon = null;
			
			if (value instanceof Group)
			{
				text = ((Group)value).name;
				icon = ((Group)value).isIdentitiesGroup() ? this._identitiesIcon : this._groupIcon;
			}
			else if (value instanceof Contact)
			{
				text = ((Contact)value).name;
				icon = ((Contact)value).isIdentity() ? this._identityIcon : this._contactIcon;
			}
			else if (value instanceof Account)
			{
				text = ((Account)value).name;
				icon = ((Account)value).service.icon;
			}
			else if (value instanceof DummyItem)
			{
				text = ((DummyItem)value).name;
				icon = ((DummyItem)value).icon;
			}
			
			JLabel comp = (JLabel)super.getListCellRendererComponent(list, text, index,
					isSelected, cellHasFocus);
			
			comp.setIconTextGap(6);
			comp.setIcon(icon);
			
			return comp;
		}
	}
}
