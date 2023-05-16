package com.inf.jpabasic;

import com.inf.jpabasic.common.exceptions.NotFoundException;
import com.inf.jpabasic.entity.UserEntity;
import com.inf.jpabasic.factory.CEntityManagerFactory;
import com.inf.jpabasic.service.UserService;
import com.inf.jpabasic.service.impl.UserServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
public class JpabasicApplication {

	public static void main(String[] args) {
//		SpringApplication.run(JpabasicApplication.class, args);
		
		/*
		01. JPA디펜던시 및 기본설정 Entity기본
		 */
		basicEntity();
		
		/*
		02. 커스텀 EntityManagerFactory 만들기
		 */
		customEntityManagerFactory();

		/*
		03. 기본적인 CRUD 사용법, JPQL : TypedQuery
		 */
		try {
			crudSimpleExample();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}


	}

	private static void crudSimpleExample() throws IOException {

		CEntityManagerFactory.initialization();
		UserService userService = new UserServiceImpl();

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		while (true) {
			System.out.println("Input your Command // [command] [email] [name]");
			System.out.println("Command : insert, select, list, updateName, delete");
			String commandLine = br.readLine();
			String[] splitCommand = commandLine.split(" ");

			// 별도 값 검증하는 로직은 추가하지 않음
			if (splitCommand[0].equalsIgnoreCase("exit")) {
				System.out.println("System closed");
				break;

			} else if (splitCommand[0].equalsIgnoreCase("insert")) {
				UserEntity userEntity = new UserEntity(splitCommand[1], splitCommand[2],
						LocalDateTime.now(), LocalDateTime.now());
				userService.saveUser(userEntity);

			} else if (splitCommand[0].equalsIgnoreCase("select")) {
				Optional<UserEntity> userEntity = userService.getUser(splitCommand[1]);
				if (userEntity.isPresent()) {
					UserEntity user = userEntity.get();
					System.out.println("email : " + user.getEmail());
					System.out.println("name : " + user.getName());
					System.out.println("created date : " + user.getCreatedAt());
					System.out.println("updated date : " + user.getUpdatedAt());

				} else {
					System.out.println("값을 찾을 수 없습니다.");
				}

			} else if (splitCommand[0].equalsIgnoreCase("list")) {

				List<UserEntity> userEntities = userService.getUserList();

				if (userEntities.isEmpty()) {
					System.out.println("값이 없습니다.");

				} else {
					userEntities.forEach(
							userEntity -> System.out.println("email : " + userEntity.getEmail()
									+ ", name : " + userEntity.getName()
									+ ", created Date : " +
									userEntity.getCreatedAt()
									+ ", updated Date : " +
									userEntity.getUpdatedAt()));
				}

			} else if (splitCommand[0].equalsIgnoreCase("updateName")) {

				try {
					userService.updateUserName(splitCommand[1], splitCommand[2]);
					System.out.println("갱신 완료");

				} catch (NotFoundException e) {
					System.out.println("값이 존재하지 않습니다.");
				}

			} else if (splitCommand[0].equalsIgnoreCase("delete")) {

				try {
					userService.deleteUser(splitCommand[1]);
					System.out.println("해당 데이터를 삭제하였습니다.");

				} catch (NotFoundException e) {
					System.out.println("값이 존재하지 않습니다.");
				}

			} else {
				System.out.println(
						"Please input Correct Command. ex) exit, insert, select, list, updateName, delete");
			}

		}

		CEntityManagerFactory.close();
	}

	private static void customEntityManagerFactory() {
		CEntityManagerFactory.initialization();

		EntityManager entityManager = CEntityManagerFactory.createEntityManger();

		// EntityManager에서 트랜잭션을 가져와 관리하기 위한 객체 생성
		EntityTransaction entityTransaction = entityManager.getTransaction();

		try {
			// 트랜잭션을 시작해야 DB를 조작할 수 있음
			entityTransaction.begin();

			// 저장하고자 하는 엔티티 객체를 생성
			UserEntity userEntity = new UserEntity("thinkground.flature@gmail.com", "Flature",
					LocalDateTime.now(), LocalDateTime.now());

			// UserEntity 객체를 Persistence Context에 추가
			entityManager.persist(userEntity);

			// 실제 DB에 반영
			entityTransaction.commit();

		} catch (Exception e) {
			e.printStackTrace();

			// 예외가 발생했을 경우 트랜잭션 롤백 진행
			entityTransaction.rollback();

		} finally {

			// 엔티티 매니저 종료. JDBC에서 Connection 종료하는 것과 동일한 기능으로 보면 됨
			entityManager.close();
		}

		// 팩토리 종료. 커넥션 풀 자원을 반환
		CEntityManagerFactory.close();
	}

	public static void basicEntity(){
		// EntityManagerFactory는 EntityManager를 생성하기 위한 팩토리 인터페이스로 persistence 단위별로 팩토리를 생성
		EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(
				"basicjpa"); // persistence.xml 파일에 기입한 이름을 적어줘야 함

		System.out.println("Check 1");

		// EntityManager 객체를 생성
		// EntityManager 는 Persistence Context와 Entity를 관리
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		System.out.println("Check 2");

		// EntityManager에서 트랜잭션을 가져와 관리하기 위한 객체 생성
		EntityTransaction entityTransaction = entityManager.getTransaction();

		System.out.println("Check 3");

		try {
			// 트랜잭션을 시작해야 DB를 조작할 수 있음
			entityTransaction.begin();

			System.out.println("Check 4");

			// 저장하고자 하는 엔티티 객체를 생성
			UserEntity userEntity = new UserEntity("thinkground.flature@gmail.com", "Flature",
					LocalDateTime.now(), LocalDateTime.now());

			System.out.println("Check 5");

			// UserEntity 객체를 Persistence Context에 추가
			entityManager.persist(userEntity);

			System.out.println("Check 6");

			// 실제 DB에 반영
			entityTransaction.commit();

			System.out.println("Check 7");

		} catch (Exception e) {
			e.printStackTrace();

			// 예외가 발생했을 경우 트랜잭션 롤백 진행
			entityTransaction.rollback();

		} finally {

			// 엔티티 매니저 종료. JDBC에서 Connection 종료하는 것과 동일한 기능으로 보면 됨
			entityManager.close();
		}

		System.out.println("Check 8");

		// 팩토리 종료. 커넥션 풀 자원을 반환
		entityManagerFactory.close();
	}

}
