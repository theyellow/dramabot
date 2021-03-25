package dramabot.service.model;

import java.util.ArrayList;
import java.util.List;

public class CsvTransfer<T extends CsvBean> {

    private List<String[]> csvStringList;

    private List<T> csvList;

    public CsvTransfer() {
        // empty object should exist
    }

    public List<String[]> getCsvStringList() {
        if (null != csvStringList) return csvStringList;
        return new ArrayList<>();
    }

    public void setCsvStringList(List<String[]> csvStringList) {
        this.csvStringList = csvStringList;
    }

    public void addLine(String[] line) {
        if (null == csvList) csvStringList = new ArrayList<>();
        csvStringList.add(line);
    }

    public List<T> getCsvList() {
        if (null != csvList) return csvList;
        return new ArrayList<>();
    }

    public void setCsvList(List<T> csvList) {
        this.csvList = csvList;
    }
}
