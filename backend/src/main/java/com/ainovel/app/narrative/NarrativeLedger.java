package com.ainovel.app.narrative;
import com.ainovel.app.v2.model.V2ManuscriptBranch;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity @Table(name="narrative_ledgers")
public class NarrativeLedger {
    @Id private java.util.UUID branchId;
    @OneToOne(fetch=FetchType.LAZY) @MapsId @JoinColumn(name="branch_id")
    @OnDelete(action=OnDeleteAction.CASCADE)
    private V2ManuscriptBranch branch;
    @Column(nullable=false) private long revision;
    @Version private long lockVersion;

    public java.util.UUID getBranchId() { return branchId; }
    public void setBranchId(java.util.UUID value) { this.branchId = value; }
    public V2ManuscriptBranch getBranch() { return branch; }
    public void setBranch(V2ManuscriptBranch value) { this.branch = value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { this.revision = value; }
    public long getLockVersion() { return lockVersion; }
    public void setLockVersion(long value) { this.lockVersion = value; }
}
