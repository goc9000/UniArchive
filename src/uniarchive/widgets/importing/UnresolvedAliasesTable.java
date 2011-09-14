/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets.importing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import uniarchive.models.archive.FreeAccount;
import uniarchive.models.import_common.Alias;
import uniarchive.widgets.TableWithFillColumn;

/**
 * Class for an aliases table used in the "unresolved aliases query"
 * page of the Gaim Import dialog.
 */
public class UnresolvedAliasesTable extends TableWithFillColumn
{
	private static final long serialVersionUID = 1L;
	
	protected AccountEditor _accountEditor;
	
	/**
	 * Constructor.
	 */
	public UnresolvedAliasesTable()
	{
		super();
	
		this._accountEditor = new AccountEditor();
		
		// Configure table properties
		this.setAutoCreateColumnsFromModel(false);
		this.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		this.setShowHorizontalLines(true);
		this.setShowVerticalLines(true);
		this.setGridColor(new Color(0.75f,0.75f,0.85f));
		this.setModel(new Model());
		
		// Create columns
		TableColumn aliasColumn = new TableColumn(Model.COLUMN_INDEX_ALIAS);
		aliasColumn.setPreferredWidth(this.getFont().getSize()*20);
		aliasColumn.setCellRenderer(new AliasRenderer());
		
		TableColumn accountColumn = new TableColumn(Model.COLUMN_INDEX_ACCOUNT);
		accountColumn.setCellRenderer(new AccountRenderer());
		accountColumn.setCellEditor(this._accountEditor);
		
		// Add columns
		this.addColumn(aliasColumn);
		this.addColumn(accountColumn);
		this.setFillColumn(accountColumn);
	}
	
	/**
	 * Loads data into the table.
	 * 
	 * @param aliases A list of aliases to be resolved
	 */
	public void loadData(List<Alias> aliases)
	{
		((Model)this.getModel()).loadData(aliases);
	}
	
	/**
	 * Gets data from the table.
	 * 
	 * @return A list of aliases
	 */
	public List<Alias> getData()
	{
		return ((Model)this.getModel()).getData();
	}
	
	/**
	 * Sets the list of accounts used in resolving
	 * aliases.
	 * 
	 * @param A list of accounts
	 */
	public void setAccounts(List<FreeAccount> accounts)
	{
		this._accountEditor.accounts = new ArrayList<FreeAccount>(accounts);
	}
	
	/**
	 * Gets the list of accounts used in resolving
	 * aliases.
	 * 
	 * @return A list of accounts
	 */
	public List<FreeAccount> getAccounts()
	{
		return new ArrayList<FreeAccount>(this._accountEditor.accounts);
	}
	
	/**
	 * Internal class for rendering an Alias cell
	 * in this data table. 
	 */
	protected static class AliasRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			Alias alias = (Alias)value;
			
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, alias.name, isSelected, hasFocus,
					row, column);
			
			renderer.setIcon(alias.service.icon);
			renderer.setIconTextGap(6);
			
			return renderer;
		}
	}
	
	/**
	 * Internal class for rendering an Account cell
	 * in this data table. 
	 */
	protected static class AccountRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		
		protected Font _normalFont;
		protected Font _italicFont;
		
		/**
		 * Constructor.
		 */
		public AccountRenderer()
		{
			super();
			
			this._normalFont = this.getFont();
			this._italicFont = this.getFont().deriveFont(Font.ITALIC);
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column)
		{
			FreeAccount account = (FreeAccount)value;
			String text = (account != null) ? account.name : "Click to select an account";
			
			JLabel renderer = (JLabel)super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
			
			if (account != null)
			{
				renderer.setIcon(account.service.icon);
				renderer.setIconTextGap(6);
				renderer.setFont(this._normalFont);
			}
			else
			{
				renderer.setIcon(null);
				renderer.setFont(this._italicFont);
			}
			
			return renderer;
		}
	}
	
	/**
	 * Internal class for editing an Account cell in this data table.
	 */
	protected static class AccountEditor extends DefaultCellEditor
	{
		private static final long serialVersionUID = 1L;
		protected static final String NEW_ACCOUNT_TOKEN = "new account";
		
		public List<FreeAccount> accounts;
		
		protected JTable _table = null;
		protected Alias _editedItem = null;
		protected int _editedItemRow = 0;
		
		/**
		 * Constructor.
		 */
		public AccountEditor()
		{
			super(new JComboBox(new DefaultComboBoxModel()));
			
			this.accounts = new ArrayList<FreeAccount>();
		
			// Customize the editor combobox
			JComboBox combo = (JComboBox)this.getComponent();
			combo.setRenderer(new AccountEditor.Renderer());
		}
		
		@Override
		public Object getCellEditorValue()
		{
			Object cellValue = super.getCellEditorValue();
			
			if (cellValue == NEW_ACCOUNT_TOKEN)
			{
				// Note: although we could pop a dialog here to get
				// the name of the new account and return it as the
				// cell editor value, this causes unacceptable glitches
				// in the UI. Therefore, we will return null, but program
				// a popup event for execution in a very short time.
				cellValue = null;
				
				Timer timer = new Timer(1, new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent evt)
					{
						_popupCreateAccountDialog();
					}
				});
				
				timer.setRepeats(false);
				timer.start();
			}
			
			return cellValue;
		}
		
		/**
		 * Pops up the Create New Account dialog in response to the
		 * timer event defined in getCellEditorValue().
		 */
		protected void _popupCreateAccountDialog()
		{
			Model tableModel = (Model)this._table.getModel();
			Alias alias = tableModel.getRecord(this._editedItemRow);
			
			String newAccName = JOptionPane.showInputDialog(this._table,
					"Input the name of the new account:",
					"Create New Account", JOptionPane.PLAIN_MESSAGE);
			
			if (newAccName == null) return;
			
			FreeAccount newAccount = new FreeAccount(alias.service, newAccName);
			
			int index = this.accounts.indexOf(newAccount);
			if (index >= 0)
				newAccount = this.accounts.get(index);
			else
				this.accounts.add(0, newAccount);
			
			alias.resolution = newAccount;
			tableModel.fireTableCellUpdated(this._editedItemRow, Model.COLUMN_INDEX_ALIAS);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
				int row, int column)
		{
			Model tableModel = (Model)table.getModel();
			Alias alias = tableModel.getRecord(row);
			
			this._table = table;
			this._editedItemRow = row;
			
			JComboBox combo = (JComboBox)super.getTableCellEditorComponent(table, value, isSelected, row, column);
			
			// Load accounts having the same service as the alias
			DefaultComboBoxModel comboModel = (DefaultComboBoxModel)combo.getModel();
			comboModel.removeAllElements();
			for (FreeAccount account : this.accounts)
				if (account.service == alias.service) comboModel.addElement(account);
			comboModel.addElement(NEW_ACCOUNT_TOKEN);
			comboModel.setSelectedItem(value);
			
			return combo;
		}
			
		/**
		 * Internal class for defining a cell renderer
		 * for the combobox
		 */
		protected static class Renderer extends DefaultListCellRenderer
		{
			private static final long serialVersionUID = 1L;

			protected Font _normalFont;
			protected Font _italicFont;
			
			/**
			 * Constructor.
			 */
			public Renderer()
			{
				super();
				
				this._normalFont = this.getFont();
				this._italicFont = this.getFont().deriveFont(Font.ITALIC);
			}
			
			@Override
			public Component getListCellRendererComponent(JList list,
					Object value, int index, boolean isSelected,
					boolean cellHasFocus)
			{
				String text = "";
				ImageIcon icon = null;
				boolean italic = false;
				
				if (value == null)
				{
					text = "No account selected";
					italic = true;
				}
				if (value == AccountEditor.NEW_ACCOUNT_TOKEN)
				{
					text = "Create new account...";
					italic = true;
				}
				if (value instanceof FreeAccount)
				{
					text = ((FreeAccount)value).name;
					icon = ((FreeAccount)value).service.icon;
				}
				
				JLabel comp = (JLabel)super.getListCellRendererComponent(
						list, text, index, isSelected, cellHasFocus);
				
				comp.setFont(italic ? this._italicFont : this._normalFont);
				comp.setIconTextGap(6);
				comp.setIcon(icon);
				
				return comp;
			}
			
			
		}
	}
	
	/**
	 * Internal class defining the model for the Accounts Table.
	 */
	protected static class Model extends AbstractTableModel
	{
		private static final long serialVersionUID = 1L;
		
		public static final int COLUMN_INDEX_ALIAS = 0;
		public static final int COLUMN_INDEX_ACCOUNT = 1;
		
		protected Alias[] _data;
		
		/**
		 * Constructor.
		 */
		public Model()
		{
			this._data = new Alias[0];	
		}
		
		/**
		 * Loads data into the model.
		 * 
		 * @param aliases A list of aliases to be resolved
		 */
		public void loadData(List<Alias> aliases)
		{
			this._data = new Alias[aliases.size()];
			for (int i=0; i<aliases.size(); i++)
			{
				Alias alias = aliases.get(i);
				this._data[i] = new Alias(alias.service, alias.name, alias.resolution);
			}
			
			this.fireTableDataChanged();
		}
		
		/**
		 * Gets all the data in the model.
		 * 
		 * @return A list of aliases.
		 */
		public List<Alias> getData()
		{
			List<Alias> aliases = new ArrayList<Alias>();
			
			for (Alias alias : this._data) aliases.add(alias);
			
			return aliases;
		}
		
		/**
		 * Returns the record at a given index.
		 * 
		 * @param index A zero-based record index
		 * @return An Alias record
		 */
		public Alias getRecord(int index)
		{
			return this._data[index];
		}
		
		@Override
		public int getColumnCount()
		{
			return 2;
		}

		@Override
		public int findColumn(String columnName)
		{
			return super.findColumn(columnName);
		}

		@Override
		public Class<?> getColumnClass(int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_ALIAS: return Alias.class;
				case COLUMN_INDEX_ACCOUNT: return FreeAccount.class;
				default: return null;
			}
		}

		@Override
		public String getColumnName(int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_ALIAS: return "Alias";
				case COLUMN_INDEX_ACCOUNT: return "Corresponding account";
				default: return "N/A";
			}
		}

		@Override
		public boolean isCellEditable(int row, int column)
		{
			return (column == COLUMN_INDEX_ACCOUNT);
		}

		@Override
		public int getRowCount()
		{
			return this._data.length;
		}
		
		@Override
		public Object getValueAt(int row, int column)
		{
			switch(column)
			{
				case COLUMN_INDEX_ALIAS: return this._data[row];
				case COLUMN_INDEX_ACCOUNT: return this._data[row].resolution;
				default: return null;
			}
		}
		
		@Override
		public void setValueAt(Object value, int row, int column)
		{
			if ((column == COLUMN_INDEX_ACCOUNT) && (value != null))
			{
				this._data[row].resolution = (FreeAccount)value;
			}
		}
	}
}
