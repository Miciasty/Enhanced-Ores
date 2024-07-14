/*

package nsk.enhanced;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.junit.Test;


import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class DatabaseTest {

    private List<Region> regions = new ArrayList<>();
    private SessionFactory sessionFactory;



    @Test
    public void TestDatabase() {

        sessionFactory = new Configuration().configure().buildSessionFactory();

        /*StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .configure()
                .build();

        Metadata metadata = new MetadataSources(registry).buildMetadata();

        SchemaExport schemaExport = new SchemaExport();
        schemaExport.drop(EnumSet.of(TargetType.DATABASE), metadata);

        SessionFactory sessionFactory = metadata.buildSessionFactory();
        Session session = sessionFactory.openSession();

        session.beginTransaction();*/

/*

        loadRegionsFromDatabase();

        getLogger().info("Regions: " + regions.size());

    }


    public void loadRegionsFromDatabase() {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Region> query = builder.createQuery(Region.class);
            query.from(Region.class);

            List<Region> result = session.createQuery(query).getResultList();
            regions.addAll(result);

            session.getTransaction().commit();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public <T> CompletableFuture<Void> saveEntityAsync(T entity) {

        return CompletableFuture.runAsync(() -> {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                session.saveOrUpdate(entity);
                session.getTransaction().commit();
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

}

 */
