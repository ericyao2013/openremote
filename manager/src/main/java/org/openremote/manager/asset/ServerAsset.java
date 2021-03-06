/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.asset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.jts.JTS;
import org.hibernate.annotations.Check;
import org.hibernate.spatial.dialect.postgis.PGGeometryTypeDescriptor;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetType;
import org.openremote.model.value.ObjectValue;
import org.openremote.model.value.Values;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Date;

/**
 * This can only be used on the server and offers types (such as
 * {@link org.geolatte.geom.Geometry}) which can not be serialized
 * or compiled on the client and constructors for query result mapping.
 */
@Entity(name = "Asset")
@Table(name = "ASSET")
@Check(constraints = "ID != PARENT_ID")
// TODO Write on-insert/update SQL trigger that validates the asset realm, it must match the parent's realm
public class ServerAsset extends Asset {

    /**
     * Easy conversion between types, we copy all properties (not a deep copy!)
     */
    public static ServerAsset map(Asset asset, ServerAsset serverAsset) {
        return map(asset, serverAsset, null, null, null, null, null, null, null);
    }

    public static ServerAsset map(Asset asset, ServerAsset serverAsset,
                                  String overrideName,
                                  String overrideRealmId,
                                  String overrideParentId,
                                  String overrideType,
                                  Double[] overrideLocation,
                                  Boolean overrideAccessPublicRead,
                                  ObjectValue overrideAttributes) {
        serverAsset.setVersion(asset.getVersion());
        serverAsset.setName(overrideName != null ? overrideName : asset.getName());
        if (overrideType != null) {
            serverAsset.setType(overrideType);
        } else {
            serverAsset.setType(asset.getType());
        }

        serverAsset.setAccessPublicRead(overrideAccessPublicRead != null ? overrideAccessPublicRead : asset.isAccessPublicRead());

        serverAsset.setParentId(overrideParentId != null ? overrideParentId : asset.getParentId());
        serverAsset.setParentName(null);
        serverAsset.setParentType(null);

        serverAsset.setRealmId(overrideRealmId != null ? overrideRealmId : asset.getRealmId());
        serverAsset.setTenantRealm(null);
        serverAsset.setTenantDisplayName(null);

        GeometryFactory geometryFactory = new GeometryFactory();

        if (overrideLocation != null) {
            serverAsset.setLocation(geometryFactory.createPoint(new Coordinate(
                overrideLocation[0],
                overrideLocation[1]
            )));
        } else if (asset.getCoordinates() != null && asset.getCoordinates().length == 2) {
            serverAsset.setLocation(geometryFactory.createPoint(new Coordinate(
                asset.getCoordinates()[0],
                asset.getCoordinates()[1]
            )));
        } else {
            serverAsset.setLocation(null);
        }

        serverAsset.setAttributes(overrideAttributes != null ? overrideAttributes : asset.getAttributes());

        return serverAsset;
    }

    @Column(name = "LOCATION")
    @Access(AccessType.PROPERTY)
    @JsonIgnore
    protected Point location;

    public ServerAsset() {
    }

    @SuppressWarnings("unchecked")
    public ServerAsset(String id, long version, Date createdOn, String name, String type, boolean accessPublicRead,
                       String parentId, String parentName, String parentType,
                       String realmId, String tenantRealm, String tenantDisplayName,
                       Object location,
                       Array path, String attributes) throws SQLException {
        super(
            id, version, createdOn, name, type, accessPublicRead,
            parentId, parentName, parentType,
            realmId, tenantRealm, tenantDisplayName,
            path != null ? (String[]) path.getArray() : null,
            attributes != null && attributes.length() > 0
                ? Values.instance().<ObjectValue>parse(attributes).orElse(null)
                : null
        );

        Point position = null;

        if (location != null) {
            Geometry geomPoint = PGGeometryTypeDescriptor.toGeometry(location);
            if (geomPoint instanceof org.geolatte.geom.Point) {
                position = (Point) JTS.to(geomPoint);
            }
        }

        setLocation(position);
    }

    public ServerAsset(@NotNull String name, @NotNull AssetType type) {
        this(name, type, null, null);
    }

    public ServerAsset(@NotNull String name, @NotNull String type) {
        this(name, type, false, null, null);
    }

    public ServerAsset(@NotNull String name, @NotNull AssetType type, Asset parent) {
        this(name, type, parent, null);
    }

    public ServerAsset(@NotNull String name, @NotNull String type, Asset parent) {
        this(name, type, false, parent, null);
    }

    public ServerAsset(@NotNull String name, @NotNull AssetType type, Asset parent, String realmId) {
        super(name, type, parent, realmId);
    }

    public ServerAsset(@NotNull String name, @NotNull String type, boolean accessPublicRead, Asset parent, String realmId) {
        super(name, type, accessPublicRead, parent, realmId);
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
        setCoordinates(this, location);
    }

    public static void setCoordinates(Asset asset, Point location) {
        if (location == null) {
            asset.setCoordinates((double[]) null);
        } else {
            asset.setCoordinates(
                location.getCoordinate().getOrdinate(Coordinate.X),
                location.getCoordinate().getOrdinate(Coordinate.Y)
            );
        }
    }
}
