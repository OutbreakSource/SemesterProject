package customerrelationsmanagement;

import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.SeriesException;
import org.jfree.data.time.*;
import org.jfree.data.xy.XYDataset;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class ChartMaker {
	public static final double BAR_THICCKNESS = 4.0;
	public static final NumberFormat CURRENCY =
	 NumberFormat.getCurrencyInstance();
	Crud crud;
	
	public ChartMaker(Crud crud) {
		
		this.crud = crud;
	}
	
	private boolean checkValidity(String time)
	throws SQLException {
		crud.setWorkingTable("daily_analysis");
		return crud.exists("fiscal_date", time);
	}
	
	private JFreeChart getBarChart
	 (String time, ChartType type)
	throws SQLException {
		
		Object[][] data = crud.getRecords(
		 "SELECT " + type.getColumn() + " FROM daily_analysis " +
		 "WHERE fiscal_date = '" + time + "'");
		String str = data[0][0] + "";
		if(str.contains("null")) {
			str = str.substring(0, str.indexOf("null") - 1);
		}
		String[] strArray = str.stripTrailing().split("\t");
		String[] labels = new String[strArray.length];
		double[] values = new double[strArray.length];
		
		for(int i = 0; i < strArray.length; i++) {
			String[] datum = strArray[i].split(" ");
			labels[i] = datum[0];
			
			values[i] = Double.parseDouble(datum[1]);
		}
		
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		
		for(int i = 0; i < values.length; i++) {
			dataset.addValue(values[i], labels[i], labels[i]);
		}
		
		JFreeChart barChart = ChartFactory
		 .createBarChart(
		  type.toString() + " on " + time, type.getColumn(), type
		   .getYAxis(), dataset,
		  PlotOrientation.VERTICAL, true, true, false);
		
		((BarRenderer)barChart.getCategoryPlot().getRenderer())
		 .setItemMargin(-values.length / BAR_THICCKNESS);
		
		((NumberAxis)barChart.getCategoryPlot().getRangeAxis())
		 .setNumberFormatOverride(CURRENCY);
		
		return barChart;
	}
	
	public JFreeChart getBarChart(BigDecimal[] ratios, int[] labels) {
		DefaultCategoryDataset dataSet = new DefaultCategoryDataset();
		
		for(int i = 0; i < ratios.length; i++) {
			dataSet.addValue(ratios[i], labels[i], labels[i]);
		}
		
		JFreeChart barChart = ChartFactory.createBarChart(
		 "System Performance", "Number of files", "Files Per Second", dataSet,
		 PlotOrientation.VERTICAL, true, true, false);
		
		((BarRenderer)barChart.getCategoryPlot().getRenderer())
		 .setItemMargin(-ratios.length / BAR_THICCKNESS);
		
		((NumberAxis) barChart.getCategoryPlot().getRangeAxis()).setTickUnit(new NumberTickUnit(1));
		
		((NumberAxis)barChart.getCategoryPlot().getRangeAxis()).
		 setNumberFormatOverride(NumberFormat.getNumberInstance());;
		
		/*
		CategoryPlot plot = (CategoryPlot) barChart.getPlot();
		NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		rangeAxis.setRange(0, 100);
		if revert worked , you should still be able to see this comment block
		 */
		
		return barChart;
	}
	
	public JFreeChart getChart(String time, ChartType type)
	throws SQLException, ParseException {
		
		if(!checkValidity(time)) { return null; }
		switch(type) {
			case TOP_CUSTOMERS, TOP_PRODUCTS -> {
				return getBarChart(time, type);
			}
			case DAILY_ASSETS, DAILY_ORDER_COUNTS, DAILY_INCOME, DAILY_REVENUE -> {
				return getTimeSeriesChart(time, type);
			}
		}
		throw new IllegalArgumentException();
	}
	public JFreeChart getChart(String time, ChartType type, long[] intervals, int[] numberOfLines, int count){
		int maxNumberOfFiles = 0;
		final TimeSeries series = new TimeSeries(type.toString());
		for(int i = 0; i < count; i++){
			maxNumberOfFiles = Math.max(maxNumberOfFiles, numberOfLines[i]);
			LocalDateTime t = (new Timestamp(intervals[i])).toLocalDateTime();
			int minute = t.getMinute();
			int hour = t.getHour();
			int day = t.getDayOfMonth();
			int month = t.getMonthValue();
			int year = t.getYear();
				try {
					series.add(new Minute(minute, hour, day, month, year),
					 BigInteger.valueOf(numberOfLines[i]));
				} catch ( SeriesException e ) {
					System.err.println("Error adding to series");
				}
			}
		return
		 ChartFactory.createTimeSeriesChart(
		  type.toString(), "Date", "Total",
		  new TimeSeriesCollection(series),
		  false, false, false);
		
	}
	private JFreeChart getTimeSeriesChart(String time, ChartType type)
	throws SQLException {
		
		Object[][] data = crud.getRecords(
		 "SELECT fiscal_date," + type.getColumn() +
		 " FROM daily_analysis WHERE fiscal_date <= '" + time + "'");
		final TimeSeries series = new TimeSeries(type.toString());
		
		MathContext context = new MathContext(13);
		for(Object[] datum: data) {
			LocalDateTime t = ((Timestamp)datum[0]).toLocalDateTime();
			int minute = t.getMinute();
			int hour = t.getHour();
			int day = t.getDayOfMonth();
			int month = t.getMonthValue();
			int year = t.getYear();
			
			switch(type) {
				case DAILY_ORDER_COUNTS -> series.add(
				 new Minute(minute, hour, day, month, year),
				 ((Integer)datum[1])
				);
				
				case DAILY_ASSETS, DAILY_INCOME, DAILY_REVENUE, TOP_CUSTOMERS, TOP_PRODUCTS -> series
				 .add(
				  new Minute(minute, hour, day, month, year),
				  ((BigDecimal)datum[1]).round(context)
				 );
			}
		}
		JFreeChart timeSeriesChart =
		 ChartFactory.createTimeSeriesChart(
		  type.toString(), "Date", "Total",
		  new TimeSeriesCollection(series),
		  false, false, false);
		
		((NumberAxis)timeSeriesChart.getXYPlot().getRangeAxis())
		 .setNumberFormatOverride(CURRENCY);
		
		return timeSeriesChart;
	}
}
