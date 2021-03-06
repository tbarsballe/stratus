/* (c) Planet Labs Inc. - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.rest.catalog;

import stratus.config.StratusConfigProps;
import stratus.config.WebXmlConfig;
import stratus.controller.GwcServiceController;
import stratus.gwc.config.*;
import stratus.redis.RedisFacadeTestSupport;
import stratus.redis.cache.CachingFilter;
import stratus.redis.cache.rest.RestCachingInterceptor;
import stratus.redis.cache.rest.preloaders.DataStorePreloader;
import stratus.redis.cache.rest.preloaders.DataStoresPreloader;
import stratus.redis.catalog.RedisCatalogFacade;
import stratus.redis.catalog.RedisCatalogImportResourcesConfig;
import stratus.redis.config.RedisConfigProps;
import stratus.redis.geoserver.RedisGeoServerFacade;
import stratus.wcs.WCSConfig;
import stratus.wcs.redis.geoserver.info.WCSInfoClassRegisteringBean;
import stratus.wfs.WFSConfig;
import stratus.wfs.redis.geoserver.info.WFSInfoClassRegisteringBean;
import stratus.wms.WMSConfig;
import stratus.wms.redis.geoserver.info.WMSInfoClassRegisteringBean;
import stratus.redis.index.CacheProperties;
import stratus.redis.index.RedisLayerIndexFacade;
import stratus.redis.repository.RedisRepositoryImpl;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.w3c.dom.Document;

import javax.servlet.Filter;
import java.util.Collections;
import java.util.List;

import static org.geoserver.rest.RestBaseController.ROOT_PATH;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        /* Catalog */
        RedisCatalogFacade.class, RedisGeoServerFacade.class, RedisLayerIndexFacade.class,
        CacheProperties.class, CatalogImpl.class, GWCWithEmbeddedRedisConfig.class, StratusConfigProps.class,
        RedisCatalogImportResourcesConfig.class, RedisRepositoryImpl.class, RedisConfigProps.class,
        /* OWS */
        WMSInfoClassRegisteringBean.class, WFSInfoClassRegisteringBean.class, WCSInfoClassRegisteringBean.class,
        WMSConfig.class, WFSConfig.class, WCSConfig.class,
        /* GWC */
        RedisServerConfiguration.class, RedisGeoServerTileLayerConfiguration.class, GwcServiceController.class,
        RedisGridSetConfiguration.class, RedisBlobStoreConfiguration.class, StratusDefaultingConfiguration.class,
        /* Cache preloaders */
        WebXmlConfig.class, RestCachingInterceptor.class, DataStorePreloader.class, DataStoresPreloader.class},
        properties = {"stratus.catalog.redis.caching.enable-rest-caching=true", "spring.main.allow-bean-definition-overriding=true"})
public class RedisDataStoreFileUploadWFSTest extends DataStoreFileUploadWFSTest {
    @Autowired
    private RedisFacadeTestSupport redisTestSupport;

    @Autowired
    CachingFilter cachingFilter;

    @Autowired
    private RedisCatalogImportResourcesConfig importResourcesConfig;

    @Override
    protected void setUpSpring(List<String> springContextLocations) {
        importResourcesConfig.buildFilteredApplicationContextXmlResourceList(springContextLocations);
    }

    @Override
    protected void onTearDown(SystemTestData testData) {
        redisTestSupport.repository.flush();
    }

    @Override
    public void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        redisTestSupport.repository.flush();
        redisTestSupport.setCatalogFacade(GeoServerSystemTestSupport.applicationContext);
        redisTestSupport.setGeoServerFacade(GeoServerSystemTestSupport.applicationContext);
    }

    @Override
    public void testShapeFileUploadIntoExisting() throws Exception {
        Catalog cat = getCatalog();
        assertNull(cat.getDataStoreByName("gs", "foo_h2"));

        String xml =
                "<dataStore>" +
                        " <name>foo_h2</name>" +
                        " <type>H2</type>" +
                        " <connectionParameters>" +
                        "<namespace>" + MockData.DEFAULT_URI + "</namespace>" +
                        "<database>./target/foo</database>" +
                        "<dbtype>h2</dbtype>" +
                        " </connectionParameters>" +
                        "<workspace>gs</workspace>" +
                        "</dataStore>";

        post(ROOT_PATH+"/workspaces/gs/datastores", xml);

        DataStoreInfo ds = cat.getDataStoreByName("gs", "foo_h2");
        assertNotNull(ds);

        assertTrue(cat.getFeatureTypesByDataStore(ds).isEmpty());

        byte[] bytes = shpZipAsBytes();
        put( ROOT_PATH+"/workspaces/gs/datastores/foo_h2/file.shp", bytes, "application/zip");

        assertFalse(cat.getFeatureTypesByDataStore(ds).isEmpty());

        Document dom = getAsDOM( "wfs?request=getfeature&typename=gs:pds" );
        assertFeatures( dom );
    }

    //Test temporarily disabled due to build box errors - TODO: Investigate and fix.
    @Override
    public void testShapeFileUploadExternal() throws Exception {

    }

    @Override
    protected List<Filter> getFilters() {
        return Collections.singletonList(cachingFilter);
    }
}
