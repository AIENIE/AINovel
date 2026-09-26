-- Scalar admin list queries sort in the database and always break ties by ID.
CREATE INDEX idx_admin_stories_page ON stories(updated_at, id);
CREATE INDEX idx_admin_worlds_page ON worlds(updated_at, id);
CREATE INDEX idx_admin_manuscripts_page ON manuscripts(updated_at, id);
CREATE INDEX idx_admin_slop_page ON slop_quality_runs(created_at, id);
CREATE INDEX idx_admin_plot_page ON plot_quality_runs(created_at, id);
