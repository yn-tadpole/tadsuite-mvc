package tadsuite.mvc.jdbc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计算页码相关信息的类
 * @author tadpole
 *
 */
public class JdbcPage {

	public final static int SHOW_PAGE_COUNT=10;
	
	private ArrayList<LinkedHashMap<String, Object>> dataList;
	private int pageSize;
	private int currentPage;
	private int totalPages;
	private int totalRows;
	private int firstRow;
	private int lastRow;
	private int showFirstPage;
	private int showLastPage;
	private boolean hasPrevPage=false;
	private int prevPage;
	private boolean hasNextPage=false;
	private int nextPage;

	public JdbcPage(int totalRows, int pageSize, int currentPage) {
		this.totalRows=totalRows;
		this.pageSize=pageSize;
		this.currentPage=currentPage;
		calculate();
	}
	
	private void calculate() {
		totalPages=(int)(totalRows-1)/pageSize + 1;
		if (currentPage>totalPages) {
			currentPage=totalPages;
		} else if (currentPage<1) {
			currentPage=1;
		}
		firstRow=(currentPage-1)*pageSize;
		lastRow=firstRow+pageSize;
		
		showFirstPage=((int)(currentPage-1)/SHOW_PAGE_COUNT)*SHOW_PAGE_COUNT+1;
		showLastPage=Math.min(totalPages, showFirstPage+SHOW_PAGE_COUNT-1);
		
		hasPrevPage=currentPage> 1;
		prevPage=hasPrevPage ? currentPage-1 : currentPage;
		hasNextPage=currentPage<totalPages;
		nextPage=hasNextPage ? currentPage+1 : currentPage;
	}
	
	
	public void setDataList(ArrayList<LinkedHashMap<String, Object>> dataList) {
		this.dataList = dataList;
	}
	
	public Map<String, Object> getMap() {
		LinkedHashMap<String, Object> map=new LinkedHashMap<>();
		map.put("dataList", dataList);
		map.put("totalRows", totalRows);
		map.put("firstRow", firstRow);
		map.put("lastRow", lastRow);
		map.put("pageSize", pageSize);
		map.put("totalPages", totalPages);
		map.put("currentPage", currentPage);
		map.put("hasPrevPage", hasPrevPage);
		map.put("prevPage", prevPage);
		map.put("hasNextPage", hasNextPage);
		map.put("nextPage", nextPage);
		map.put("showFirstPage", showFirstPage);
		map.put("showLastPage", showLastPage);
		return map;
	}
	
	public ArrayList<LinkedHashMap<String, Object>> getDataList() {
		return dataList;
	}
	
	public int getPageSize() {
		return pageSize;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public int getTotalRows() {
		return totalRows;
	}

	public int getFirstRow() {
		return firstRow;
	}

	public int getLastRow() {
		return lastRow;
	}

	public int getShowFirstPage() {
		return showFirstPage;
	}

	public int getShowLastPage() {
		return showLastPage;
	}

	public boolean hasPrevPage() {
		return hasPrevPage;
	}

	public int getPrevPage() {
		return prevPage;
	}

	public boolean hasNextPage() {
		return hasNextPage;
	}

	public int getNextPage() {
		return nextPage;
	}
}
