package dramabot.hibernate.bootstrap.model;

import javax.persistence.*;

@Entity
@Table
public class CatalogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(length = 1800)
    private String entryText;

    @Column(length = 140)
    private String entryAuthor;

    @Column(length = 60)
    private String entryType;

    public CatalogEntry(String entryText, String entryAuthor, String entryType) {
        this.entryText = entryText;
        this.entryAuthor = entryAuthor;
        this.entryType = entryType;
    }

    public CatalogEntry() {
    }

    public String getEntryText() {
        return entryText;
    }

    public void setEntryText(String entryText) {
        this.entryText = entryText;
    }

    public String getEntryAuthor() {
        return entryAuthor;
    }

    public void setEntryAuthor(String entryAuthor) {
        this.entryAuthor = entryAuthor;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

}
