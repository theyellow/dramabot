package dramabot.service.model;

import com.opencsv.bean.CsvBindByName;

public class CatalogEntryBean implements CsvBean {

    @CsvBindByName(column = "text")
    private String text;
    @CsvBindByName(column = "author")
    private String author;
    @CsvBindByName(column = "type")
    private String type;

    public CatalogEntryBean(String text, String author, String type) {
        this.text = text;
        this.author = author;
        this.type = type;
    }

    public CatalogEntryBean() {
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
