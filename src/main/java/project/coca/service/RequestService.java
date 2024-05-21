package project.coca.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.coca.domain.group.CoGroup;
import project.coca.domain.group.GroupManager;
import project.coca.domain.group.GroupMember;
import project.coca.domain.personal.Friend;
import project.coca.domain.personal.Member;
import project.coca.domain.personal.PersonalSchedule;
import project.coca.domain.request.*;
import project.coca.dto.response.common.error.AlreadyReportedException;
import project.coca.repository.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional
public class RequestService {

    private final MemberRepository memberRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final GroupRepository groupRepository;
    private final GroupRequestRepository groupRequestRepository;
    private final GroupService groupService;
    private final FriendService friendService;
    private final ScheduleRequestRepository scheduleRequestRepository;
    private final GroupManagerRepository groupManagerRepository;
    private final RequestedScheduleRepository requestedScheduleRepository;
    private final PersonalScheduleRepository personalScheduleRepository;
    private final PersonalScheduleService personalScheduleService;
    private final FriendRepository friendRepository;

    public RequestService(MemberRepository memberRepository,
                          FriendRequestRepository friendRequestRepository,
                          GroupRepository groupRepository,
                          GroupRequestRepository groupRequestRepository,
                          GroupService groupService,
                          FriendService friendService,
                          ScheduleRequestRepository scheduleRequestRepository,
                          GroupManagerRepository groupManagerRepository,
                          RequestedScheduleRepository requestedScheduleRepository,
                          PersonalScheduleRepository personalScheduleRepository, PersonalScheduleService personalScheduleService, FriendRepository friendRepository) {
        this.memberRepository = memberRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.groupRepository = groupRepository;
        this.groupRequestRepository = groupRequestRepository;
        this.groupService = groupService;
        this.friendService = friendService;
        this.scheduleRequestRepository = scheduleRequestRepository;
        this.groupManagerRepository = groupManagerRepository;
        this.requestedScheduleRepository = requestedScheduleRepository;
        this.personalScheduleRepository = personalScheduleRepository;
        this.personalScheduleService = personalScheduleService;
        this.friendRepository = friendRepository;
    }

    private static PersonalSchedule getPersonalSchedule(ScheduleRequest scheduleRequest) {
        PersonalSchedule personalSchedule = new PersonalSchedule();
        RequestedSchedule requestedSchedule = scheduleRequest.getRequestedSchedule();
        personalSchedule.setMember(scheduleRequest.getReceiver());
        personalSchedule.setTitle(requestedSchedule.getTitle());
        personalSchedule.setDescription(requestedSchedule.getDescription());
        personalSchedule.setColor(requestedSchedule.getColor());
        personalSchedule.setStartTime(requestedSchedule.getStartTime());
        personalSchedule.setEndTime(requestedSchedule.getEndTime());
        personalSchedule.setIsPrivate(false);
        return personalSchedule;
    }

    /**
     * 36-1. 친구 요청 등록
     */
    public void addFriendRequest(String fromId, String toId) {
        // 1. fromId 회원 검증
        Member fromMember = memberRepository.findById(fromId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 2. toId 회원 검증
        Member toMember = memberRepository.findById(toId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 3. 친구 요청 생성
        FriendRequest req = new FriendRequest();
        req.setSender(fromMember);
        req.setReceiver(toMember);
        req.setRequestStatus(RequestStatus.PENDING);
        // 4. 친구 요청 등록
        friendRequestRepository.save(req);
    }

    /**
     * 36-2. 그룹 초대 요청 등록
     */
    public void addGroupRequest(String fromId, String toId, Long groupId) {
        // 1. fromId 회원 검증
        Member fromMember = memberRepository.findById(fromId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 2. toId 회원 검증
        Member toMember = memberRepository.findById(toId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 3. 그룹 검증
        CoGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("그룹이 조회되지 않습니다."));
        // 4. 그룹 요청 생성
        GroupRequest req = new GroupRequest();
        req.setSender(fromMember);
        req.setReceiver(toMember);
        req.setCoGroup(group);
        req.setRequestStatus(RequestStatus.PENDING);

        // 5. 그룹 요청 등록
        groupRequestRepository.save(req);
    }

    /**
     * 36-3-a. 빈일정 추가 요청 등록 to GroupMember
     */
    public void addScheduleRequestToGroupMember(Long groupId, GroupManager manager, RequestedSchedule schedule, List<GroupMember> groupMembers) {
        // 요청자가 그룹 매너저인가
        CoGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("그룹이 조회되지 않습니다."));
        GroupManager findManager = groupManagerRepository.checkUserIsManager(manager.getGroupManager().getId(), group.getId())
                .orElseThrow(() -> new NoSuchElementException("그룹 매니저가 조회되지 않습니다."));
        // 요청된 일정부터 저장
        RequestedSchedule savedSchedule = requestedScheduleRepository.save(schedule);
        // 일정 요청 목록을 만들어서 각 요청마다 요청된 일정, sender, receiver 적용
        List<ScheduleRequest> requests = groupMembers.stream()
                .map(groupMember -> {
                    ScheduleRequest newRequest = new ScheduleRequest();
                    newRequest.setSender(findManager.getGroupManager());
                    newRequest.setRequestedSchedule(savedSchedule);
                    newRequest.setReceiver(groupMember.getGroupMember());
                    return newRequest;
                })
                .collect(Collectors.toList());
        // 일정 요청 목록 모두 save
        scheduleRequestRepository.saveAll(requests);
    }

    /**
     * 36-3-b. 빈일정 추가 요청 등록 to Friend
     */
    public void addScheduleRequestToFriend(Member member, RequestedSchedule schedule, List<Friend> friends) {
        // 요청자가 회원인가
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));

        // 요청된 일정부터 저장
        RequestedSchedule savedSchedule = requestedScheduleRepository.save(schedule);

        // 저장된 일정으로 각 친구에게 요청
        List<ScheduleRequest> requests = new ArrayList<>();
        for (Friend friend : friends) {
            if (friend.getId() != null) { // ID가 null이 아닌 경우에만 요청 생성
                Friend friendMember = friendRepository.findById(friend.getId())
                        .orElseThrow(() -> new NoSuchElementException("친구가 조회되지 않습니다."));
                ScheduleRequest newRequest = new ScheduleRequest();
                newRequest.setSender(findMember);
                newRequest.setRequestedSchedule(savedSchedule);
                newRequest.setReceiver(friendMember.getOpponent());
                newRequest.setRequestStatus(RequestStatus.PENDING);
                requests.add(newRequest);
            }
        }

        // 본인 것에도 추가
        ScheduleRequest selfRequest = new ScheduleRequest();
        selfRequest.setSender(findMember);
        selfRequest.setRequestedSchedule(savedSchedule);
        selfRequest.setReceiver(findMember);
        selfRequest.setRequestStatus(RequestStatus.PENDING);
        requests.add(selfRequest);

        scheduleRequestRepository.saveAll(requests);
    }


    /**
     * 37-1. 친구 요청 목록 조회
     */
    public List<FriendRequest> findFriendRequests(String memberId) {
        // 1. 회원 검증
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 2. 회원으로 친구 요청 목록 조회
        return friendRequestRepository.findByReceiverOrderByCreatedDateDesc(member);
    }

    /**
     * 37-2. 그룹 초대 요청 목록 조회
     */
    public List<GroupRequest> findGroupRequests(String memberId) {
        // 1. 회원 검증
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 2. 회원으로 그룹 요청 목록 조회
        return groupRequestRepository.findByReceiverOrderByCreatedDateDesc(member);
    }

    /**
     * 37-3. 빈일정 추가 요청 목록 조회
     */
    public List<ScheduleRequest> findScheduleRequests(String memberId) {
        // 1. 회원 검증
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 조회되지 않습니다."));
        // 2. 회원으로 일정 추가 요청 목록 조회
        return scheduleRequestRepository.findByReceiverOrderByCreatedDateDesc(member);
    }

    /**
     * 38-1. 친구 요청 수정 => 수락의 경우 친구 등록
     */
    public void updateFriendRequest(Long requestId, RequestStatus status) {
        // 1. 요청 조회
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("요청이 조회되지 않습니다."));
        // 1-1. 이미 처리된 요청 예외
        if (request.getRequestStatus() != RequestStatus.PENDING) {
            throw new NoSuchElementException("이미 처리된 요청입니다.");
        }
        // 2. 수정 내용 적용
        request.setRequestStatus(status);
        // 3. status가 승인의 경우 친구 등록
        if (status == RequestStatus.ACCEPTED) {
            friendService.saveFriend(request.getSender(), request.getReceiver());
        }
        // 4. 수정된 친구 요청 저장
        friendRequestRepository.save(request);
    }

    /**
     * 38-2. 그룹 초대 요청 수정
     * 이미 보내진 여러 개의 요청은 어떻게 처리한담..
     */
    public void updateGroupRequest(Long requestId, RequestStatus status) {
        // 1. 요청 조회
        GroupRequest groupRequest = groupRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("요청이 조회되지 않습니다."));
        // 1-1. 이미 처리된 요청 예외
        if (groupRequest.getRequestStatus() != RequestStatus.PENDING) {
            throw new AlreadyReportedException("이미 처리된 요청입니다.");
        }
        // 2. 수정 내용 적용
        groupRequest.setRequestStatus(status);
        // 3. status가 승인의 경우 그룹 참가하기
        if (status == RequestStatus.ACCEPTED) {
            // 3-1. 그룹 참가
            groupService.joinGroup(groupRequest.getReceiver(), groupRequest.getCoGroup());
        }
        // 4. 수정된 그룹 요청 저장
        groupRequestRepository.save(groupRequest);
    }

    /**
     * 38-3. 빈일정 추가 요청 수정
     */
    public void updateScheduleRequest(Long requestId, RequestStatus status) {
        // 1. 요청 조회
        ScheduleRequest scheduleRequest = scheduleRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("요청이 조회되지 않습니다."));
        // 1-1. 이미 처리된 요청 예외
        if (scheduleRequest.getRequestStatus() != RequestStatus.PENDING) {
            throw new AlreadyReportedException("이미 처리된 요청입니다.");
        }
        // 2. 수정 내용 적용
        scheduleRequest.setRequestStatus(status);
        // 3. status가 승인의 경우 개인 일정에 추가하기
        if (status == RequestStatus.ACCEPTED) {
            PersonalSchedule personalSchedule = getPersonalSchedule(scheduleRequest);
            personalScheduleRepository.save(personalSchedule);
        }
        // 4. 수정된 그룹 요청 저장
        scheduleRequestRepository.save(scheduleRequest);
    }

    /**
     * 39-1. 친구 요청 삭제
     */
    public void deleteFriendRequest(Long requestId) {
        // 1. 요청 조회
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("요청이 조회되지 않습니다."));
        // 2. 요청 삭제
        friendRequestRepository.delete(request);
    }

    /**
     * 39-2. 그룹 초대 요청 삭제
     */
    public void deleteGroupRequest(Long requestId) {
        // 1. 요청 조회
        GroupRequest groupRequest = groupRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("요청이 조회되지 않습니다."));
        // 2. 요청 삭제
        groupRequestRepository.delete(groupRequest);
    }

    /**
     * 39-3. 빈일정 추가 요청 삭제
     */
    public void deleteScheduleRequest(Long requestId) {
        // 1. 요청 조회
        ScheduleRequest scheduleRequest = scheduleRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("요청이 조회되지 않습니다."));
        // 2. 요청 삭제
        scheduleRequestRepository.delete(scheduleRequest);
    }
}