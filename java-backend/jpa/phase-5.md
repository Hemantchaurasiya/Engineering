# Phase 5: Query Mastery - Complete Guide with Examples

## 5.1 JPQL (Java Persistence Query Language)

JPQL is an object-oriented query language that operates on JPA entities rather than database tables. It's similar to SQL but works with entity objects and their properties.

### 5.1.1 JPQL Syntax and Basic Structure

```java
// Basic entity for our examples
@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    private String email;
    private BigDecimal salary;
    
    @Enumerated(EnumType.STRING)
    private Department department;
    
    @Temporal(TemporalType.DATE)
    private Date hireDate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;
    
    @OneToMany(mappedBy = "manager", cascade = CascadeType.ALL)
    private List<Employee> subordinates = new ArrayList<>();
    
    // constructors, getters, setters
}

public enum Department {
    ENGINEERING, MARKETING, SALES, HR, FINANCE
}
```

### 5.1.2 SELECT Statements - Entity and Scalar Queries

```java
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. Simple entity selection
    @Query("SELECT e FROM Employee e WHERE e.department = :department")
    List<Employee> findByDepartment(@Param("department") Department department);
    
    // 2. Scalar queries - selecting specific fields
    @Query("SELECT e.firstName, e.lastName, e.salary FROM Employee e WHERE e.salary > :minSalary")
    List<Object[]> findEmployeeBasicInfo(@Param("minSalary") BigDecimal minSalary);
    
    // 3. Constructor expressions - type-safe projections
    @Query("SELECT new com.example.dto.EmployeeDto(e.firstName, e.lastName, e.email) " +
           "FROM Employee e WHERE e.department = :dept")
    List<EmployeeDto> findEmployeeDtosByDepartment(@Param("dept") Department dept);
    
    // 4. Single value selection
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department = :department")
    Long countByDepartment(@Param("department") Department department);
}

// DTO class for constructor expression
public class EmployeeDto {
    private String firstName;
    private String lastName;
    private String email;
    
    public EmployeeDto(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }
    // getters and setters
}
```

### 5.1.3 FROM Clause and Entity References

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. Simple FROM with entity alias
    @Query("SELECT e FROM Employee e WHERE e.salary > 50000")
    List<Employee> findHighEarners();
    
    // 2. FROM with entity path expressions
    @Query("SELECT e FROM Employee e WHERE e.manager.firstName = 'John'")
    List<Employee> findEmployeesWithManagerNamed();
    
    // 3. Multiple entities in FROM (Cartesian product)
    @Query("SELECT e1, e2 FROM Employee e1, Employee e2 " +
           "WHERE e1.department = e2.department AND e1.id != e2.id")
    List<Object[]> findColleaguesInSameDepartment();
}
```

### 5.1.4 WHERE Clause and Conditional Expressions

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. Basic comparisons
    @Query("SELECT e FROM Employee e WHERE e.salary BETWEEN :min AND :max")
    List<Employee> findBySalaryRange(@Param("min") BigDecimal min, @Param("max") BigDecimal max);
    
    // 2. String operations
    @Query("SELECT e FROM Employee e WHERE e.email LIKE %:domain")
    List<Employee> findByEmailDomain(@Param("domain") String domain);
    
    // 3. NULL checks
    @Query("SELECT e FROM Employee e WHERE e.manager IS NULL")
    List<Employee> findTopLevelManagers();
    
    // 4. Collection operations
    @Query("SELECT e FROM Employee e WHERE e.department IN :departments")
    List<Employee> findByDepartments(@Param("departments") List<Department> departments);
    
    // 5. Date comparisons
    @Query("SELECT e FROM Employee e WHERE e.hireDate > :date")
    List<Employee> findHiredAfter(@Param("date") Date date);
    
    // 6. Complex conditions
    @Query("SELECT e FROM Employee e WHERE " +
           "(e.department = 'ENGINEERING' AND e.salary > 80000) OR " +
           "(e.department = 'SALES' AND e.salary > 60000)")
    List<Employee> findHighPerformers();
}
```

### 5.1.5 JOIN Operations

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. INNER JOIN - only employees with managers
    @Query("SELECT e FROM Employee e INNER JOIN e.manager m WHERE m.department = :dept")
    List<Employee> findEmployeesWithManagerInDepartment(@Param("dept") Department dept);
    
    // 2. LEFT JOIN - all employees, with manager info if available
    @Query("SELECT e, m FROM Employee e LEFT JOIN e.manager m")
    List<Object[]> findAllEmployeesWithManagerInfo();
    
    // 3. FETCH JOIN - eager loading to avoid N+1 problem
    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.subordinates")
    List<Employee> findAllWithSubordinates();
    
    // 4. Multiple joins
    @Query("SELECT e FROM Employee e " +
           "INNER JOIN e.manager m " +
           "INNER JOIN m.manager gm " +
           "WHERE gm.firstName = :grandManagerName")
    List<Employee> findByGrandManagerName(@Param("grandManagerName") String name);
    
    // 5. Join with conditions
    @Query("SELECT e FROM Employee e " +
           "LEFT JOIN e.subordinates s " +
           "WHERE s.salary > :minSalary OR s.salary IS NULL")
    List<Employee> findManagersWithHighEarningSubordinates(@Param("minSalary") BigDecimal minSalary);
}
```

### 5.1.6 Subqueries

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. Correlated subquery
    @Query("SELECT e FROM Employee e WHERE e.salary > " +
           "(SELECT AVG(e2.salary) FROM Employee e2 WHERE e2.department = e.department)")
    List<Employee> findAboveAverageSalaryInDepartment();
    
    // 2. Non-correlated subquery with EXISTS
    @Query("SELECT e FROM Employee e WHERE EXISTS " +
           "(SELECT s FROM Employee s WHERE s.manager = e AND s.salary > 70000)")
    List<Employee> findManagersWithHighEarningSubordinates();
    
    // 3. Subquery with IN
    @Query("SELECT e FROM Employee e WHERE e.department IN " +
           "(SELECT DISTINCT e2.department FROM Employee e2 WHERE e2.salary > 100000)")
    List<Employee> findInDepartmentsWithHighEarners();
    
    // 4. Subquery with ALL
    @Query("SELECT e FROM Employee e WHERE e.salary > ALL " +
           "(SELECT e2.salary FROM Employee e2 WHERE e2.department = 'HR')")
    List<Employee> findEarningMoreThanAllHR();
}
```

### 5.1.7 Aggregate Functions and GROUP BY

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. Basic aggregates
    @Query("SELECT COUNT(e), AVG(e.salary), MAX(e.salary), MIN(e.salary) FROM Employee e")
    Object[] getEmployeeStatistics();
    
    // 2. GROUP BY with aggregates
    @Query("SELECT e.department, COUNT(e), AVG(e.salary) FROM Employee e GROUP BY e.department")
    List<Object[]> getDepartmentStatistics();
    
    // 3. HAVING clause
    @Query("SELECT e.department, COUNT(e) FROM Employee e " +
           "GROUP BY e.department HAVING COUNT(e) > :minCount")
    List<Object[]> findDepartmentsWithMinEmployees(@Param("minCount") Long minCount);
    
    // 4. Complex grouping with joins
    @Query("SELECT m.firstName, m.lastName, COUNT(s) FROM Employee m " +
           "LEFT JOIN m.subordinates s " +
           "GROUP BY m.id, m.firstName, m.lastName " +
           "HAVING COUNT(s) >= :minSubordinates")
    List<Object[]> findManagersWithMinSubordinates(@Param("minSubordinates") Long min);
}
```

### 5.1.8 ORDER BY and Sorting

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    // 1. Simple sorting
    @Query("SELECT e FROM Employee e ORDER BY e.salary DESC, e.lastName ASC")
    List<Employee> findAllOrderedBySalaryAndName();
    
    // 2. Conditional sorting
    @Query("SELECT e FROM Employee e ORDER BY " +
           "CASE e.department " +
           "WHEN 'ENGINEERING' THEN 1 " +
           "WHEN 'SALES' THEN 2 " +
           "ELSE 3 END, e.lastName")
    List<Employee> findAllWithCustomDepartmentOrder();
    
    // 3. Sorting with aggregates
    @Query("SELECT e.department, COUNT(e) as empCount FROM Employee e " +
           "GROUP BY e.department ORDER BY empCount DESC")
    List<Object[]> findDepartmentsByEmployeeCount();
}
```

## 5.2 Criteria API - Type-Safe Dynamic Queries

The Criteria API provides a programmatic way to build queries that are type-safe and can be constructed dynamically at runtime.

### 5.2.1 Basic Criteria API Setup

```java
@Service
public class EmployeeService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. Simple criteria query
    public List<Employee> findEmployeesBySalaryRange(BigDecimal min, BigDecimal max) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        
        query.select(employee)
             .where(cb.between(employee.get("salary"), min, max));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // 2. Scalar query with criteria
    public List<Object[]> getDepartmentSalaryStats() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Employee> employee = query.from(Employee.class);
        
        query.multiselect(
            employee.get("department"),
            cb.count(employee),
            cb.avg(employee.get("salary")),
            cb.max(employee.get("salary"))
        ).groupBy(employee.get("department"));
        
        return entityManager.createQuery(query).getResultList();
    }
}
```

### 5.2.2 Dynamic Query Building

```java
@Service
public class EmployeeSearchService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Employee> searchEmployees(EmployeeSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Dynamic conditions based on search criteria
        if (criteria.getFirstName() != null) {
            predicates.add(cb.like(cb.lower(employee.get("firstName")), 
                                 "%" + criteria.getFirstName().toLowerCase() + "%"));
        }
        
        if (criteria.getDepartment() != null) {
            predicates.add(cb.equal(employee.get("department"), criteria.getDepartment()));
        }
        
        if (criteria.getMinSalary() != null) {
            predicates.add(cb.greaterThanOrEqualTo(employee.get("salary"), criteria.getMinSalary()));
        }
        
        if (criteria.getMaxSalary() != null) {
            predicates.add(cb.lessThanOrEqualTo(employee.get("salary"), criteria.getMaxSalary()));
        }
        
        if (criteria.getHiredAfter() != null) {
            predicates.add(cb.greaterThan(employee.get("hireDate"), criteria.getHiredAfter()));
        }
        
        // Apply all predicates
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        // Dynamic sorting
        if (criteria.getSortBy() != null) {
            if (criteria.isSortDescending()) {
                query.orderBy(cb.desc(employee.get(criteria.getSortBy())));
            } else {
                query.orderBy(cb.asc(employee.get(criteria.getSortBy())));
            }
        }
        
        TypedQuery<Employee> typedQuery = entityManager.createQuery(query);
        
        // Pagination
        if (criteria.getPage() != null && criteria.getSize() != null) {
            typedQuery.setFirstResult(criteria.getPage() * criteria.getSize());
            typedQuery.setMaxResults(criteria.getSize());
        }
        
        return typedQuery.getResultList();
    }
}

// Search criteria class
public class EmployeeSearchCriteria {
    private String firstName;
    private Department department;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Date hiredAfter;
    private String sortBy;
    private boolean sortDescending;
    private Integer page;
    private Integer size;
    
    // constructors, getters, setters
}
```

### 5.2.3 Criteria API with Joins

```java
@Service
public class AdvancedEmployeeService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. Inner join example
    public List<Employee> findEmployeesWithManagerInDepartment(Department dept) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        Join<Employee, Employee> manager = employee.join("manager", JoinType.INNER);
        
        query.select(employee)
             .where(cb.equal(manager.get("department"), dept));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // 2. Left join with fetch
    public List<Employee> findEmployeesWithSubordinates() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        employee.fetch("subordinates", JoinType.LEFT);
        
        query.select(employee).distinct(true);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // 3. Complex join with conditions
    public List<Employee> findManagersWithHighEarningTeam(BigDecimal minTeamSalary) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> manager = query.from(Employee.class);
        Join<Employee, Employee> subordinate = manager.join("subordinates", JoinType.LEFT);
        
        query.select(manager)
             .where(cb.greaterThan(subordinate.get("salary"), minTeamSalary))
             .groupBy(manager.get("id"))
             .having(cb.greaterThan(cb.count(subordinate), 2L));
        
        return entityManager.createQuery(query).getResultList();
    }
}
```

### 5.2.4 Subqueries in Criteria API

```java
@Service
public class SubqueryExamples {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. Correlated subquery
    public List<Employee> findAboveAverageSalaryInDept() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        
        // Subquery for average salary in same department
        Subquery<Double> subquery = query.subquery(Double.class);
        Root<Employee> subEmployee = subquery.from(Employee.class);
        subquery.select(cb.avg(subEmployee.get("salary")))
                .where(cb.equal(subEmployee.get("department"), employee.get("department")));
        
        query.select(employee)
             .where(cb.greaterThan(employee.get("salary"), subquery));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // 2. EXISTS subquery
    public List<Employee> findManagersWithSubordinates() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        
        Subquery<Employee> subquery = query.subquery(Employee.class);
        Root<Employee> subordinate = subquery.from(Employee.class);
        subquery.select(subordinate)
                .where(cb.equal(subordinate.get("manager"), employee));
        
        query.select(employee)
             .where(cb.exists(subquery));
        
        return entityManager.createQuery(query).getResultList();
    }
}
```

### 5.2.5 Metamodel API for Type Safety

```java
// Generate metamodel classes with annotation processor
@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Employee.class)
public abstract class Employee_ {
    public static volatile SingularAttribute<Employee, Long> id;
    public static volatile SingularAttribute<Employee, String> firstName;
    public static volatile SingularAttribute<Employee, String> lastName;
    public static volatile SingularAttribute<Employee, String> email;
    public static volatile SingularAttribute<Employee, BigDecimal> salary;
    public static volatile SingularAttribute<Employee, Department> department;
    public static volatile SingularAttribute<Employee, Date> hireDate;
    public static volatile SingularAttribute<Employee, Employee> manager;
    public static volatile ListAttribute<Employee, Employee> subordinates;
}

// Usage with metamodel
@Service
public class TypeSafeQueryService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Employee> findByDepartmentTypeSafe(Department dept) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> employee = query.from(Employee.class);
        
        // Type-safe property access
        query.select(employee)
             .where(cb.equal(employee.get(Employee_.department), dept))
             .orderBy(cb.desc(employee.get(Employee_.salary)));
        
        return entityManager.createQuery(query).getResultList();
    }
}
```

## 5.3 Query By Example (QBE)

QBE provides a simple way to create queries using example objects.

### 5.3.1 Basic QBE Usage

```java
@RestController
public class EmployeeController {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @PostMapping("/employees/search")
    public List<Employee> searchEmployees(@RequestBody Employee probe) {
        // Create example from the probe object
        Example<Employee> example = Example.of(probe);
        return employeeRepository.findAll(example);
    }
    
    @PostMapping("/employees/search-with-matcher")
    public List<Employee> searchWithMatcher(@RequestBody EmployeeSearchRequest request) {
        Employee probe = new Employee();
        probe.setFirstName(request.getFirstName());
        probe.setDepartment(request.getDepartment());
        
        ExampleMatcher matcher = ExampleMatcher.matching()
            .withIgnoreCase() // Ignore case for string properties
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING) // Like %value%
            .withIgnoreNullValues() // Ignore null properties
            .withMatcher("firstName", ExampleMatcher.GenericPropertyMatchers.startsWith());
        
        Example<Employee> example = Example.of(probe, matcher);
        return employeeRepository.findAll(example);
    }
}
```

### 5.3.2 Advanced ExampleMatcher Configuration

```java
@Service
public class EmployeeSearchService {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    public List<Employee> complexSearch(String name, Department dept, BigDecimal minSalary) {
        Employee probe = new Employee();
        probe.setFirstName(name);
        probe.setDepartment(dept);
        
        ExampleMatcher matcher = ExampleMatcher.matching()
            .withIgnoreNullValues()
            .withIgnorePaths("salary", "hireDate", "id") // Ignore specific properties
            .withMatcher("firstName", 
                ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())
            .withTransformer("department", 
                value -> value.map(dept -> dept.toString().toUpperCase()));
        
        Example<Employee> example = Example.of(probe, matcher);
        
        // For complex conditions like salary range, use Specifications
        return employeeRepository.findAll(example);
    }
}
```

## 5.4 Specifications - Reusable Query Logic

Specifications provide a way to create reusable, composable query logic.

### 5.4.1 Basic Specifications

```java
// Repository must extend JpaSpecificationExecutor
public interface EmployeeRepository extends JpaRepository<Employee, Long>, 
                                          JpaSpecificationExecutor<Employee> {
}

// Specification utility class
public class EmployeeSpecifications {
    
    public static Specification<Employee> hasDepartment(Department department) {
        return (root, query, criteriaBuilder) -> 
            department == null ? null : criteriaBuilder.equal(root.get("department"), department);
    }
    
    public static Specification<Employee> salaryBetween(BigDecimal min, BigDecimal max) {
        return (root, query, criteriaBuilder) -> {
            if (min == null && max == null) return null;
            if (min == null) return criteriaBuilder.lessThanOrEqualTo(root.get("salary"), max);
            if (max == null) return criteriaBuilder.greaterThanOrEqualTo(root.get("salary"), min);
            return criteriaBuilder.between(root.get("salary"), min, max);
        };
    }
    
    public static Specification<Employee> nameContains(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.trim().isEmpty()) return null;
            String pattern = "%" + name.toLowerCase() + "%";
            return criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern)
            );
        };
    }
    
    public static Specification<Employee> hiredAfter(Date date) {
        return (root, query, criteriaBuilder) ->
            date == null ? null : criteriaBuilder.greaterThan(root.get("hireDate"), date);
    }
    
    public static Specification<Employee> hasManager() {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.isNotNull(root.get("manager"));
    }
    
    public static Specification<Employee> hasSubordinates() {
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Employee> subRoot = subquery.from(Employee.class);
            subquery.select(criteriaBuilder.count(subRoot))
                    .where(criteriaBuilder.equal(subRoot.get("manager"), root));
            return criteriaBuilder.greaterThan(subquery, 0L);
        };
    }
}
```

### 5.4.2 Composing Specifications

```java
@Service
public class EmployeeSpecificationService {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    public List<Employee> findEmployees(EmployeeSearchCriteria criteria) {
        Specification<Employee> spec = Specification.where(null);
        
        // Compose specifications using and(), or(), not()
        spec = spec.and(EmployeeSpecifications.hasDepartment(criteria.getDepartment()))
                   .and(EmployeeSpecifications.salaryBetween(criteria.getMinSalary(), criteria.getMaxSalary()))
                   .and(EmployeeSpecifications.nameContains(criteria.getName()))
                   .and(EmployeeSpecifications.hiredAfter(criteria.getHiredAfter()));
        
        // Complex combinations
        if (criteria.isManagersOnly()) {
            spec = spec.and(EmployeeSpecifications.hasSubordinates());
        }
        
        if (criteria.isExcludeTopLevel()) {
            spec = spec.and(EmployeeSpecifications.hasManager());
        }
        
        return employeeRepository.findAll(spec);
    }
    
    // Using OR conditions
    public List<Employee> findHighValueEmployees() {
        Specification<Employee> highSalary = EmployeeSpecifications.salaryBetween(
            new BigDecimal("80000"), null);
        Specification<Employee> isManager = EmployeeSpecifications.hasSubordinates();
        Specification<Employee> seniorEngineer = EmployeeSpecifications.hasDepartment(Department.ENGINEERING)
            .and(EmployeeSpecifications.salaryBetween(new BigDecimal("70000"), null));
        
        Specification<Employee> spec = Specification.where(highSalary)
            .or(isManager)
            .or(seniorEngineer);
        
        return employeeRepository.findAll(spec);
    }
}
```

### 5.4.3 Advanced Specifications with Joins

```java
public class AdvancedEmployeeSpecifications {
    
    // Specification with join
    public static Specification<Employee> hasManagerInDepartment(Department dept) {
        return (root, query, criteriaBuilder) -> {
            Join<Employee, Employee> managerJoin = root.join("manager", JoinType.INNER);
            return criteriaBuilder.equal(managerJoin.get("department"), dept);
        };
    }
    
    // Specification with collection join
    public static Specification<Employee> hasSubordinateWithMinSalary(BigDecimal minSalary) {
        return (root, query, criteriaBuilder) -> {
            Join<Employee, Employee> subordinatesJoin = root.join("subordinates", JoinType.INNER);
            return criteriaBuilder.greaterThanOrEqualTo(subordinatesJoin.get("salary"), minSalary);
        };
    }
    
    // Specification with fetch join for performance
    public static Specification<Employee> withSubordinatesFetched() {
        return (root, query, criteriaBuilder) -> {
            if (query.getResultType() != Long.class) { // Avoid fetch in count queries
                root.fetch("subordinates", JoinType.LEFT);
                query.distinct(true);
            }
            return null; // No additional predicate
        };
    }
    
    // Complex specification with subquery
    public static Specification<Employee> earnsMoreThanAverageInDepartment() {
        return (root, query, criteriaBuilder) -> {
            Subquery<Double> subquery = query.subquery(Double.class);
            Root<Employee> subRoot = subquery.from(Employee.class);
            subquery.select(criteriaBuilder.avg(subRoot.get("salary")))
                    .where(criteriaBuilder.equal(subRoot.get("department"), root.get("department")));
            
            return criteriaBuilder.greaterThan(root.get("salary"), subquery);
        };
    }
}
```

### 5.4.4 Custom Specification Executor

```java
// Custom interface for advanced specification operations
public interface CustomEmployeeRepository {
    Page<Employee> findWithSpecification(Specification<Employee> spec, Pageable pageable);
    List<EmployeeDto> findDtosWithSpecification(Specification<Employee> spec);
    long countWithSpecification(Specification<Employee> spec);
}

// Implementation
@Repository
public class CustomEmployeeRepositoryImpl implements CustomEmployeeRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public Page<Employee> findWithSpecification(Specification<Employee> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Employee> countRoot = countQuery.from(Employee.class);
        countQuery.select(cb.count(countRoot));
        if (spec != null) {
            countQuery.where(spec.toPredicate(countRoot, countQuery, cb));
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        // Data query
        CriteriaQuery<Employee> dataQuery = cb.createQuery(Employee.class);
        Root<Employee> dataRoot = dataQuery.from(Employee.class);
        dataQuery.select(dataRoot);
        if (spec != null) {
            dataQuery.where(spec.toPredicate(dataRoot, dataQuery, cb));
        }
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = pageable.getSort().stream()
                .map(sort -> sort.isAscending() 
                    ? cb.asc(dataRoot.get(sort.getProperty()))
                    : cb.desc(dataRoot.get(sort.getProperty())))
                .collect(Collectors.toList());
            dataQuery.orderBy(orders);
        }
        
        TypedQuery<Employee> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Employee> content = typedQuery.getResultList();
        return new PageImpl<>(content, pageable, total);
    }
    
    @Override
    public List<EmployeeDto> findDtosWithSpecification(Specification<Employee> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmployeeDto> query = cb.createQuery(EmployeeDto.class);
        Root<Employee> root = query.from(Employee.class);
        
        query.select(cb.construct(EmployeeDto.class,
            root.get("firstName"),
            root.get("lastName"),
            root.get("email")
        ));
        
        if (spec != null) {
            query.where(spec.toPredicate(root, query, cb));
        }
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public long countWithSpecification(Specification<Employee> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Employee> root = query.from(Employee.class);
        
        query.select(cb.count(root));
        if (spec != null) {
            query.where(spec.toPredicate(root, query, cb));
        }
        
        return entityManager.createQuery(query).getSingleResult();
    }
}
```

## Real-World Use Cases and Best Practices

### Use Case 1: Employee Management System

```java
@RestController
@RequestMapping("/api/employees")
public class EmployeeManagementController {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @Autowired
    private EmployeeSpecificationService specificationService;
    
    // 1. Advanced search with multiple criteria
    @GetMapping("/search")
    public ResponseEntity<Page<Employee>> searchEmployees(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) BigDecimal minSalary,
            @RequestParam(required = false) BigDecimal maxSalary,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date hiredAfter,
            @RequestParam(required = false, defaultValue = "false") boolean managersOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder()
            .name(name)
            .department(department)
            .minSalary(minSalary)
            .maxSalary(maxSalary)
            .hiredAfter(hiredAfter)
            .managersOnly(managersOnly)
            .build();
        
        Sort sort = Sort.by(sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Employee> result = specificationService.findEmployeesWithCriteria(criteria, pageable);
        return ResponseEntity.ok(result);
    }
    
    // 2. Department analytics
    @GetMapping("/analytics/departments")
    public List<DepartmentAnalytics> getDepartmentAnalytics() {
        return employeeRepository.getDepartmentAnalytics();
    }
    
    // 3. Manager effectiveness report
    @GetMapping("/analytics/managers")
    public List<ManagerEffectivenessDto> getManagerEffectiveness() {
        return employeeRepository.getManagerEffectiveness();
    }
}

// Enhanced repository with complex queries
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, 
                                          JpaSpecificationExecutor<Employee> {
    
    // Complex JPQL for department analytics
    @Query("SELECT new com.example.dto.DepartmentAnalytics(" +
           "e.department, " +
           "COUNT(e), " +
           "AVG(e.salary), " +
           "MIN(e.salary), " +
           "MAX(e.salary), " +
           "COUNT(CASE WHEN e.subordinates IS NOT EMPTY THEN 1 END)) " +
           "FROM Employee e " +
           "GROUP BY e.department " +
           "ORDER BY COUNT(e) DESC")
    List<DepartmentAnalytics> getDepartmentAnalytics();
    
    // Manager effectiveness with complex conditions
    @Query("SELECT new com.example.dto.ManagerEffectivenessDto(" +
           "m.firstName, m.lastName, " +
           "COUNT(s), " +
           "AVG(s.salary), " +
           "COUNT(CASE WHEN s.salary > 70000 THEN 1 END)) " +
           "FROM Employee m " +
           "INNER JOIN m.subordinates s " +
           "GROUP BY m.id, m.firstName, m.lastName " +
           "HAVING COUNT(s) >= 3 " +
           "ORDER BY AVG(s.salary) DESC")
    List<ManagerEffectivenessDto> getManagerEffectiveness();
    
    // Finding potential promotion candidates
    @Query("SELECT e FROM Employee e WHERE " +
           "e.salary > (SELECT AVG(e2.salary) * 1.2 FROM Employee e2 WHERE e2.department = e.department) " +
           "AND SIZE(e.subordinates) = 0 " +
           "AND e.hireDate < :cutoffDate")
    List<Employee> findPromotionCandidates(@Param("cutoffDate") Date cutoffDate);
}
```

### Use Case 2: Dynamic Reporting System

```java
@Service
public class ReportingService {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. Dynamic report generation using Criteria API
    public ReportResult generateEmployeeReport(ReportConfiguration config) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Employee> employee = query.from(Employee.class);
        
        List<Selection<?>> selections = new ArrayList<>();
        List<Expression<?>> groupByExpressions = new ArrayList<>();
        
        // Dynamic field selection based on configuration
        for (String field : config.getSelectedFields()) {
            switch (field) {
                case "department":
                    selections.add(employee.get("department"));
                    groupByExpressions.add(employee.get("department"));
                    break;
                case "count":
                    selections.add(cb.count(employee));
                    break;
                case "avgSalary":
                    selections.add(cb.avg(employee.get("salary")));
                    break;
                case "maxSalary":
                    selections.add(cb.max(employee.get("salary")));
                    break;
                case "minSalary":
                    selections.add(cb.min(employee.get("salary")));
                    break;
            }
        }
        
        query.multiselect(selections);
        
        // Apply filters
        List<Predicate> predicates = buildPredicates(cb, employee, config.getFilters());
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        // Apply grouping
        if (!groupByExpressions.isEmpty()) {
            query.groupBy(groupByExpressions);
        }
        
        List<Object[]> results = entityManager.createQuery(query).getResultList();
        return new ReportResult(config.getSelectedFields(), results);
    }
    
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Employee> root, 
                                          Map<String, Object> filters) {
        List<Predicate> predicates = new ArrayList<>();
        
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            String field = filter.getKey();
            Object value = filter.getValue();
            
            if (value != null) {
                switch (field) {
                    case "department":
                        predicates.add(cb.equal(root.get("department"), value));
                        break;
                    case "minSalary":
                        predicates.add(cb.greaterThanOrEqualTo(root.get("salary"), (BigDecimal) value));
                        break;
                    case "maxSalary":
                        predicates.add(cb.lessThanOrEqualTo(root.get("salary"), (BigDecimal) value));
                        break;
                    case "hiredAfter":
                        predicates.add(cb.greaterThan(root.get("hireDate"), (Date) value));
                        break;
                }
            }
        }
        
        return predicates;
    }
}
```

### Use Case 3: Complex Business Logic with Specifications

```java
@Component
public class EmployeeBusinessRules {
    
    // Business rule: Find employees eligible for promotion
    public static Specification<Employee> eligibleForPromotion() {
        return (root, query, criteriaBuilder) -> {
            // Must be employed for at least 2 years
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -2);
            Date twoYearsAgo = cal.getTime();
            
            Predicate tenurePredicate = criteriaBuilder.lessThan(root.get("hireDate"), twoYearsAgo);
            
            // Must earn above average in department
            Subquery<Double> avgSalarySubquery = query.subquery(Double.class);
            Root<Employee> subRoot = avgSalarySubquery.from(Employee.class);
            avgSalarySubquery.select(criteriaBuilder.avg(subRoot.get("salary")))
                           .where(criteriaBuilder.equal(subRoot.get("department"), root.get("department")));
            
            Predicate salaryPredicate = criteriaBuilder.greaterThan(root.get("salary"), avgSalarySubquery);
            
            // Must not be a manager (looking for individual contributors)
            Subquery<Long> subordinateCountSubquery = query.subquery(Long.class);
            Root<Employee> subordinateRoot = subordinateCountSubquery.from(Employee.class);
            subordinateCountSubquery.select(criteriaBuilder.count(subordinateRoot))
                                  .where(criteriaBuilder.equal(subordinateRoot.get("manager"), root));
            
            Predicate notManagerPredicate = criteriaBuilder.equal(subordinateCountSubquery, 0L);
            
            return criteriaBuilder.and(tenurePredicate, salaryPredicate, notManagerPredicate);
        };
    }
    
    // Business rule: Find at-risk employees (low performance indicators)
    public static Specification<Employee> atRiskEmployees() {
        return (root, query, criteriaBuilder) -> {
            // Low salary compared to department average
            Subquery<Double> avgSalarySubquery = query.subquery(Double.class);
            Root<Employee> subRoot = avgSalarySubquery.from(Employee.class);
            avgSalarySubquery.select(criteriaBuilder.avg(subRoot.get("salary")))
                           .where(criteriaBuilder.equal(subRoot.get("department"), root.get("department")));
            
            Predicate lowSalaryPredicate = criteriaBuilder.lessThan(
                root.get("salary"), 
                criteriaBuilder.prod(avgSalarySubquery, 0.8) // 20% below average
            );
            
            // Recent hire (less than 6 months) with below-average salary
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -6);
            Date sixMonthsAgo = cal.getTime();
            
            Predicate recentHirePredicate = criteriaBuilder.greaterThan(root.get("hireDate"), sixMonthsAgo);
            
            return criteriaBuilder.and(lowSalaryPredicate, recentHirePredicate);
        };
    }
}
```

## Performance Optimization Tips

### Query Optimization Best Practices

```java
@Service
public class OptimizedQueryService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. Use projections for read-only data
    @Query("SELECT new com.example.dto.EmployeeSummaryDto(" +
           "e.id, e.firstName, e.lastName, e.department, e.salary) " +
           "FROM Employee e WHERE e.department = :dept")
    List<EmployeeSummaryDto> findEmployeeSummaries(@Param("dept") Department dept);
    
    // 2. Avoid N+1 with fetch joins
    @Query("SELECT DISTINCT e FROM Employee e " +
           "LEFT JOIN FETCH e.manager " +
           "LEFT JOIN FETCH e.subordinates " +
           "WHERE e.department = :dept")
    List<Employee> findWithRelationshipsFetched(@Param("dept") Department dept);
    
    // 3. Use EXISTS instead of joins for filtering
    @Query("SELECT e FROM Employee e WHERE EXISTS " +
           "(SELECT 1 FROM Employee s WHERE s.manager = e AND s.salary > :minSalary)")
    List<Employee> findManagersWithHighEarningSubordinatesOptimized(@Param("minSalary") BigDecimal minSalary);
    
    // 4. Batch processing for large datasets
    @Transactional
    public void processSalaryIncrease(Department dept, BigDecimal increasePercent) {
        String jpql = "UPDATE Employee e SET e.salary = e.salary * (1 + :increase) " +
                     "WHERE e.department = :dept";
        
        entityManager.createQuery(jpql)
                    .setParameter("increase", increasePercent.divide(new BigDecimal("100")))
                    .setParameter("dept", dept)
                    .executeUpdate();
    }
}
```

### Query Debugging and Monitoring

```java
@Configuration
public class QueryLoggingConfiguration {
    
    // Enable SQL logging
    @Bean
    public Logger sqlLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        logger.setLevel(Level.DEBUG);
        return logger;
    }
}

// Custom query performance monitor
@Component
@Aspect
public class QueryPerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceMonitor.class);
    
    @Around("execution(* com.example.repository.*Repository.*(..))")
    public Object monitorRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (executionTime > 1000) { // Log slow queries
                logger.warn("Slow query detected: {} took {}ms", methodName, executionTime);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Query failed: {} - {}", methodName, e.getMessage());
            throw e;
        }
    }
}
```

## Complete Working Example

```java
// Service demonstrating all Phase 5 concepts
@Service
@Transactional(readOnly = true)
public class EmployeeQueryMasterService {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. JPQL example with complex business logic
    public List<Employee> findTopPerformers(Department dept, int limit) {
        String jpql = """
            SELECT e FROM Employee e 
            WHERE e.department = :dept 
            AND e.salary > (
                SELECT AVG(e2.salary) * 1.25 
                FROM Employee e2 
                WHERE e2.department = :dept
            )
            AND SIZE(e.subordinates) > 0
            ORDER BY e.salary DESC
        """;
        
        return entityManager.createQuery(jpql, Employee.class)
                          .setParameter("dept", dept)
                          .setMaxResults(limit)
                          .getResultList();
    }
    
    // 2. Criteria API for dynamic reporting
    public Page<EmployeeReportDto> generateEmployeeReport(
            EmployeeReportCriteria criteria, Pageable pageable) {
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Employee> countRoot = countQuery.from(Employee.class);
        List<Predicate> countPredicates = buildReportPredicates(cb, countRoot, criteria);
        countQuery.select(cb.count(countRoot));
        if (!countPredicates.isEmpty()) {
            countQuery.where(cb.and(countPredicates.toArray(new Predicate[0])));
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        // Data query
        CriteriaQuery<EmployeeReportDto> dataQuery = cb.createQuery(EmployeeReportDto.class);
        Root<Employee> dataRoot = dataQuery.from(Employee.class);
        Join<Employee, Employee> managerJoin = dataRoot.join("manager", JoinType.LEFT);
        
        dataQuery.select(cb.construct(EmployeeReportDto.class,
            dataRoot.get("id"),
            dataRoot.get("firstName"),
            dataRoot.get("lastName"),
            dataRoot.get("department"),
            dataRoot.get("salary"),
            managerJoin.get("firstName"),
            managerJoin.get("lastName"),
            cb.size(dataRoot.get("subordinates"))
        ));
        
        List<Predicate> dataPredicates = buildReportPredicates(cb, dataRoot, criteria);
        if (!dataPredicates.isEmpty()) {
            dataQuery.where(cb.and(dataPredicates.toArray(new Predicate[0])));
        }
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order sortOrder : pageable.getSort()) {
                Expression<?> sortExpression = dataRoot.get(sortOrder.getProperty());
                orders.add(sortOrder.isAscending() ? cb.asc(sortExpression) : cb.desc(sortExpression));
            }
            dataQuery.orderBy(orders);
        }
        
        List<EmployeeReportDto> content = entityManager.createQuery(dataQuery)
                                                     .setFirstResult((int) pageable.getOffset())
                                                     .setMaxResults(pageable.getPageSize())
                                                     .getResultList();
        
        return new PageImpl<>(content, pageable, total);
    }
    
    // 3. Specifications for complex business rules
    public List<Employee> findUsingBusinessRules(BusinessRuleSet rules) {
        Specification<Employee> spec = Specification.where(null);
        
        if (rules.isIncludePromotionCandidates()) {
            spec = spec.or(EmployeeBusinessRules.eligibleForPromotion());
        }
        
        if (rules.isIncludeAtRiskEmployees()) {
            spec = spec.or(EmployeeBusinessRules.atRiskEmployees());
        }
        
        if (rules.getDepartmentFilter() != null) {
            spec = spec.and(EmployeeSpecifications.hasDepartment(rules.getDepartmentFilter()));
        }
        
        return employeeRepository.findAll(spec);
    }
    
    // 4. Query by Example for flexible search
    public Page<Employee> flexibleSearch(Employee searchTemplate, Pageable pageable) {
        ExampleMatcher matcher = ExampleMatcher.matching()
            .withIgnoreNullValues()
            .withIgnoreCase()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
            .withIgnorePaths("id", "salary", "hireDate"); // Ignore non-string fields
        
        Example<Employee> example = Example.of(searchTemplate, matcher);
        return employeeRepository.findAll(example, pageable);
    }
    
    private List<Predicate> buildReportPredicates(CriteriaBuilder cb, Root<Employee> root, 
                                                EmployeeReportCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getDepartments() != null && !criteria.getDepartments().isEmpty()) {
            predicates.add(root.get("department").in(criteria.getDepartments()));
        }
        
        if (criteria.getMinSalary() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("salary"), criteria.getMinSalary()));
        }
        
        if (criteria.getMaxSalary() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("salary"), criteria.getMaxSalary()));
        }
        
        return predicates;
    }
}
```

## Testing Query Methods

```java
@DataJpaTest
class EmployeeQueryTests {
    
    @Autowired
    private TestEntityManager testEntityManager;
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    @Test
    void testComplexJPQLQuery() {
        // Setup test data
        Employee manager = new Employee("John", "Doe", "john@example.com", 
                                      new BigDecimal("90000"), Department.ENGINEERING);
        manager = testEntityManager.persistAndFlush(manager);
        
        Employee subordinate1 = new Employee("Jane", "Smith", "jane@example.com",
                                           new BigDecimal("75000"), Department.ENGINEERING);
        subordinate1.setManager(manager);
        testEntityManager.persistAndFlush(subordinate1);
        
        Employee subordinate2 = new Employee("Bob", "Johnson", "bob@example.com",
                                           new BigDecimal("72000"), Department.ENGINEERING);
        subordinate2.setManager(manager);
        testEntityManager.persistAndFlush(subordinate2);
        
        // Test the query
        List<Employee> result = employeeRepository.findTopPerformers(Department.ENGINEERING, 10);
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("John");
    }
    
    @Test
    void testSpecificationComposition() {
        // Setup test data
        Employee employee = new Employee("Alice", "Brown", "alice@example.com",
                                       new BigDecimal("85000"), Department.MARKETING);
        employee.setHireDate(Date.from(Instant.now().minus(365, ChronoUnit.DAYS)));
        testEntityManager.persistAndFlush(employee);
        
        // Test specification
        Specification<Employee> spec = EmployeeSpecifications
            .hasDepartment(Department.MARKETING)
            .and(EmployeeSpecifications.salaryBetween(new BigDecimal("80000"), new BigDecimal("90000")));
        
        List<Employee> result = employeeRepository.findAll(spec);
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Alice");
    }
    
    @Test
    void testQueryByExample() {
        // Setup test data
        Employee template = new Employee();
        template.setDepartment(Department.SALES);
        template.setFirstName("Test");
        
        Employee actual = new Employee("Test", "User", "test@example.com",
                                     new BigDecimal("60000"), Department.SALES);
        testEntityManager.persistAndFlush(actual);
        
        // Test QBE
        ExampleMatcher matcher = ExampleMatcher.matching()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.EXACT);
        
        Example<Employee> example = Example.of(template, matcher);
        List<Employee> result = employeeRepository.findAll(example);
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@example.com");
    }
}
```

## Key Takeaways for Phase 5

1. **JPQL** is powerful for complex queries but can become hard to maintain for dynamic scenarios
2. **Criteria API** shines for dynamic query building but has a steeper learning curve
3. **Specifications** provide excellent reusability and composition capabilities
4. **Query by Example** is perfect for simple, flexible search functionality
5. Always consider performance implications - use projections, fetch joins wisely, and monitor query execution times
6. Test your queries thoroughly, especially complex ones with business logic

## Next Steps

Before moving to Phase 6, make sure you:
1. Practice writing JPQL queries for various scenarios
2. Build a few dynamic query examples using Criteria API
3. Create a set of reusable Specifications for your domain
4. Implement Query by Example for at least one search feature
5. Set up query performance monitoring in your application

The concepts in Phase 5 form the foundation for advanced performance optimization techniques you'll learn in Phase 6!