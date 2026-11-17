package com.campusos.dao;

import com.campusos.model.Resource;
import com.campusos.model.ResourceStatus;
import com.campusos.model.ResourceType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** CRUD and searches for campus resources. */
public class ResourceDAO {

    private static final String BASE_SELECT = """
            SELECT r.*, u.full_name AS created_by_name
            FROM resources r
            LEFT JOIN users u ON u.user_id = r.created_by""";

    public Resource findById(Connection conn, long id) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE r.resource_id = ?", ResourceDAO::map, id);
    }

    /**
     * Loads the resource with SELECT ... FOR UPDATE inside a booking
     * transaction. The row lock serializes concurrent booking attempts for
     * the same resource so conflict checks cannot race with each other.
     */
    public Resource findByIdForUpdate(Connection conn, long id) {
        return Sql.queryOne(conn, BASE_SELECT + " WHERE r.resource_id = ? FOR UPDATE",
                ResourceDAO::map, id);
    }

    public List<Resource> findAll(Connection conn) {
        return Sql.query(conn, BASE_SELECT + " ORDER BY r.resource_name", ResourceDAO::map);
    }

    public List<Resource> findWithFilters(Connection conn, ResourceType type,
                                          ResourceStatus status, String searchText) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (type != null) {
            sql.append(" AND r.resource_type = ?");
            params.add(type.name());
        }
        if (status != null) {
            sql.append(" AND r.status = ?");
            params.add(status.name());
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (r.resource_name LIKE ? OR r.location LIKE ?)");
            String like = "%" + searchText.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY r.resource_type, r.resource_name");
        return Sql.query(conn, sql.toString(), ResourceDAO::map, params.toArray());
    }

    /** Only ACTIVE resources are bookable by students and faculty. */
    public List<Resource> findBookable(Connection conn) {
        return Sql.query(conn,
                BASE_SELECT + " WHERE r.status = 'ACTIVE' ORDER BY r.resource_type, r.resource_name",
                ResourceDAO::map);
    }

    public long countAll(Connection conn) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM resources");
    }

    public long insert(Connection conn, Resource r) {
        return Sql.insert(conn, """
                INSERT INTO resources (resource_name, resource_type, location, capacity,
                                       description, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                r.getName(), r.getType().name(), r.getLocation(), r.getCapacity(),
                r.getDescription(), r.getStatus().name(), r.getCreatedById());
    }

    public boolean update(Connection conn, Resource r) {
        return Sql.update(conn, """
                UPDATE resources
                   SET resource_name = ?, resource_type = ?, location = ?, capacity = ?,
                       description = ?, status = ?
                 WHERE resource_id = ?""",
                r.getName(), r.getType().name(), r.getLocation(), r.getCapacity(),
                r.getDescription(), r.getStatus().name(), r.getId()) > 0;
    }

    public boolean setStatus(Connection conn, long id, ResourceStatus status) {
        return Sql.update(conn, "UPDATE resources SET status = ? WHERE resource_id = ?",
                status.name(), id) > 0;
    }

    public boolean delete(Connection conn, long id) {
        return Sql.update(conn, "DELETE FROM resources WHERE resource_id = ?", id) > 0;
    }

    /** How many bookings reference this resource (blocks deletion when > 0). */
    public long countBookings(Connection conn, long id) {
        return Sql.queryLong(conn, "SELECT COUNT(*) FROM bookings WHERE resource_id = ?", id);
    }

    static Resource map(ResultSet rs) throws SQLException {
        Resource r = new Resource();
        r.setId(rs.getLong("resource_id"));
        r.setName(rs.getString("resource_name"));
        r.setType(ResourceType.valueOf(rs.getString("resource_type")));
        r.setLocation(rs.getString("location"));
        int cap = rs.getInt("capacity");
        r.setCapacity(rs.wasNull() ? null : cap);
        r.setDescription(rs.getString("description"));
        r.setStatus(ResourceStatus.valueOf(rs.getString("status")));
        long creator = rs.getLong("created_by");
        r.setCreatedById(rs.wasNull() ? null : creator);
        r.setCreatedByName(rs.getString("created_by_name"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        if (created != null) r.setCreatedAt(created.toLocalDateTime());
        if (updated != null) r.setUpdatedAt(updated.toLocalDateTime());
        return r;
    }
}
