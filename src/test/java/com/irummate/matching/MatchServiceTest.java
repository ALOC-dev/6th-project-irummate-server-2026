package com.irummate.matching;

import com.irummate.domain.matching.entity.MatchRequests;
import com.irummate.domain.matching.entity.MatchStatus;
import com.irummate.domain.matching.repository.MatchRepository;
import com.irummate.domain.matching.service.MatchingService;
import com.irummate.domain.survey.entity.UserPreferences;
import com.irummate.domain.survey.repository.UserPreferencesRepository;
import com.irummate.domain.user.entity.UserDetails;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private MatchingService matchingService;

    @Test
    void matchTest(){

        Long myUserId = 1L;

        UserPreferences myPreference = mock(UserPreferences.class);
        UserPreferences otherPreference1 = mock(UserPreferences.class);
        UserPreferences otherPreference2 = mock(UserPreferences.class);
        UserPreferences otherPreference3 = mock(UserPreferences.class);

        Users me = mock(Users.class);
        Users other1 = mock(Users.class);
        Users other2 = mock(Users.class);
        Users other3 = mock(Users.class);

        when(me.getId()).thenReturn(myUserId);

        when(me.getUserPreferences()).thenReturn(myPreference);
        when(other1.getUserPreferences()).thenReturn(otherPreference1);
        when(other2.getUserPreferences()).thenReturn(otherPreference2);
        when(other3.getUserPreferences()).thenReturn(otherPreference3);

        UserDetails myDetails = mock(UserDetails.class);
        when(myDetails.getGender()).thenReturn("MALE");

        when(me.getUserDetails()).thenReturn(myDetails);

        when(myPreference.getIsMatched()).thenReturn(false);
        when(myPreference.getRerolledAt()).thenReturn(null);
        when(myPreference.getUser()).thenReturn(me);
        when(myPreference.getSmokingStatus()).thenReturn(1);
        when(myPreference.getLifestyleVector()).thenReturn(new float[]{
                0.25f, 0.0f, 0.0f, 0.75f, 0.5f, 0.33f, 0.0f, 0.5f, 1.0f
        });

        when(usersRepository.findById(1L)).thenReturn(Optional.of(me));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(other1));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(other2));
        when(usersRepository.findById(4L)).thenReturn(Optional.of(other3));

        when(userPreferencesRepository.findByUserIdWithUserDetails(myUserId))
                .thenReturn(Optional.of(myPreference));

        when(matchRepository.findReusableCandidatesWithSmoking(eq(myUserId), eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        UserPreferencesRepository.RecommendationCandidate candidate1 = mock(UserPreferencesRepository.RecommendationCandidate.class);
        UserPreferencesRepository.RecommendationCandidate candidate2 = mock(UserPreferencesRepository.RecommendationCandidate.class);
        UserPreferencesRepository.RecommendationCandidate candidate3 = mock(UserPreferencesRepository.RecommendationCandidate.class);

        when(candidate1.getUserId()).thenReturn(2L);
        when(candidate2.getUserId()).thenReturn(3L);
        when(candidate3.getUserId()).thenReturn(4L);

        when(candidate1.getMatchPercentage()).thenReturn(88.0);
        when(candidate2.getMatchPercentage()).thenReturn(95.0);
        when(candidate3.getMatchPercentage()).thenReturn(75.0);

        when(userPreferencesRepository.findNewRecommendationCandidates(
                eq(myUserId),
                eq("MALE"),
                eq(1),
                anyString(),
                eq(3)
        )).thenReturn(List.of(
                candidate1,
                candidate2,
                candidate3
        ));

        ArgumentCaptor<MatchRequests> captor = ArgumentCaptor.forClass(MatchRequests.class);

        // when
        matchingService.match(myUserId);

        // then
        verify(matchRepository, times(3)).save(captor.capture());
        verify(myPreference).updateIsRerolled();

        List<MatchRequests> saved = captor.getAllValues();

        assertThat(saved)
                .extracting(MatchRequests::getMatchPercentage)
                .containsExactly(95.0, 88.0, 75.0);

        assertThat(saved)
                .allSatisfy(matchRequest -> {
                    assertThat(matchRequest.getUserLow().getId()).isEqualTo(1L);
                    assertThat(matchRequest.getUserLowStatus()).isEqualTo(MatchStatus.RECOMMENDED);
                    assertThat(matchRequest.getUserHighStatus()).isEqualTo(MatchStatus.NONE);
                });
    }

    @Test
    void matchIgnoreSmokingTest(){
        Long myUserId = 1L;

        UserPreferences myPreference = mock(UserPreferences.class);
        UserPreferences otherPreference1 = mock(UserPreferences.class);
        UserPreferences otherPreference2 = mock(UserPreferences.class);
        UserPreferences otherPreference3 = mock(UserPreferences.class);

        Users me = mock(Users.class);
        Users other1 = mock(Users.class);
        Users other2 = mock(Users.class);
        Users other3 = mock(Users.class);

        when(me.getId()).thenReturn(myUserId);

        when(me.getUserPreferences()).thenReturn(myPreference);
        when(other1.getUserPreferences()).thenReturn(otherPreference1);
        when(other2.getUserPreferences()).thenReturn(otherPreference2);
        when(other3.getUserPreferences()).thenReturn(otherPreference3);

        UserDetails myDetails = mock(UserDetails.class);
        when(myDetails.getGender()).thenReturn("MALE");

        when(me.getUserDetails()).thenReturn(myDetails);

        when(myPreference.getIsMatched()).thenReturn(false);
        when(myPreference.getRerolledAt()).thenReturn(null);
        when(myPreference.getUser()).thenReturn(me);
        when(myPreference.getSmokingStatus()).thenReturn(1);
        when(myPreference.getLifestyleVector()).thenReturn(new float[]{
                0.25f, 0.0f, 0.0f, 0.75f, 0.5f, 0.33f, 0.0f, 0.5f, 1.0f
        });

        when(usersRepository.findById(1L)).thenReturn(Optional.of(me));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(other1));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(other2));
        when(usersRepository.findById(4L)).thenReturn(Optional.of(other3));

        when(userPreferencesRepository.findByUserIdWithUserDetails(myUserId))
                .thenReturn(Optional.of(myPreference));

        when(matchRepository.findReusableCandidatesWithSmoking(eq(myUserId), eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        when(matchRepository.findReusableCandidatesIgnoringSmoking(eq(myUserId), any(Pageable.class)))
                .thenReturn(List.of());

        UserPreferencesRepository.RecommendationCandidate candidate1 = mock(UserPreferencesRepository.RecommendationCandidate.class);
        UserPreferencesRepository.RecommendationCandidate candidate2 = mock(UserPreferencesRepository.RecommendationCandidate.class);
        UserPreferencesRepository.RecommendationCandidate candidate3 = mock(UserPreferencesRepository.RecommendationCandidate.class);

        when(candidate1.getUserId()).thenReturn(2L);
        when(candidate2.getUserId()).thenReturn(3L);
        when(candidate3.getUserId()).thenReturn(4L);

        when(candidate1.getMatchPercentage()).thenReturn(88.0);
        when(candidate2.getMatchPercentage()).thenReturn(95.0);
        when(candidate3.getMatchPercentage()).thenReturn(75.0);


        when(userPreferencesRepository.findNewRecommendationCandidates(
                eq(myUserId),
                eq("MALE"),
                eq(1),
                anyString(),
                eq(3)
        )).thenReturn(List.of(
                candidate1,
                candidate2
        ));

        when(userPreferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(
                eq(myUserId),
                eq("MALE"),
                eq(List.of(3L,2L)),
                anyString(),
                eq(1)
        )).thenReturn(List.of(
                candidate3
        ));


        ArgumentCaptor<MatchRequests> captor = ArgumentCaptor.forClass(MatchRequests.class);

        matchingService.match(myUserId);

        verify(matchRepository, times(3)).save(captor.capture());
        verify(myPreference).updateIsRerolled();

        List<MatchRequests> saved = captor.getAllValues();

        assertThat(saved)
                .extracting(MatchRequests::getMatchPercentage)
                .containsExactly(95.0, 88.0, 75.0);

        assertThat(saved)
                .allSatisfy(matchRequest -> {
                    assertThat(matchRequest.getUserLow().getId()).isEqualTo(1L);
                    assertThat(matchRequest.getUserLowStatus()).isEqualTo(MatchStatus.RECOMMENDED);
                    assertThat(matchRequest.getUserHighStatus()).isEqualTo(MatchStatus.NONE);
                });

    }


    @Test
    void matchUnderTwoTest(){
        Long myUserId = 1L;

        UserPreferences myPreference = mock(UserPreferences.class);
        UserPreferences otherPreference1 = mock(UserPreferences.class);
        UserPreferences otherPreference2 = mock(UserPreferences.class);

        Users me = mock(Users.class);
        Users other1 = mock(Users.class);
        Users other2 = mock(Users.class);

        when(me.getId()).thenReturn(myUserId);

        when(me.getUserPreferences()).thenReturn(myPreference);
        when(other1.getUserPreferences()).thenReturn(otherPreference1);
        when(other2.getUserPreferences()).thenReturn(otherPreference2);

        UserDetails myDetails = mock(UserDetails.class);
        when(myDetails.getGender()).thenReturn("MALE");

        when(me.getUserDetails()).thenReturn(myDetails);

        when(myPreference.getIsMatched()).thenReturn(false);
        when(myPreference.getRerolledAt()).thenReturn(null);
        when(myPreference.getUser()).thenReturn(me);
        when(myPreference.getSmokingStatus()).thenReturn(1);
        when(myPreference.getLifestyleVector()).thenReturn(new float[]{
                0.25f, 0.0f, 0.0f, 0.75f, 0.5f, 0.33f, 0.0f, 0.5f, 1.0f
        });

        when(usersRepository.findById(1L)).thenReturn(Optional.of(me));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(other1));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(other2));

        when(userPreferencesRepository.findByUserIdWithUserDetails(myUserId))
                .thenReturn(Optional.of(myPreference));

        when(matchRepository.findReusableCandidatesWithSmoking(eq(myUserId), eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        when(matchRepository.findReusableCandidatesIgnoringSmoking(eq(myUserId), any(Pageable.class)))
                .thenReturn(List.of());

        UserPreferencesRepository.RecommendationCandidate candidate1 = mock(UserPreferencesRepository.RecommendationCandidate.class);
        UserPreferencesRepository.RecommendationCandidate candidate2 = mock(UserPreferencesRepository.RecommendationCandidate.class);

        when(candidate1.getUserId()).thenReturn(2L);
        when(candidate2.getUserId()).thenReturn(3L);


        when(candidate1.getMatchPercentage()).thenReturn(88.0);
        when(candidate2.getMatchPercentage()).thenReturn(95.0);


        when(userPreferencesRepository.findNewRecommendationCandidates(
                eq(myUserId),
                eq("MALE"),
                eq(1),
                anyString(),
                eq(3)
        )).thenReturn(List.of(
                candidate1,
                candidate2
        ));

        when(userPreferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(
                eq(myUserId),
                eq("MALE"),
                eq(List.of(3L,2L)),
                anyString(),
                eq(1)
        )).thenReturn(List.of(
        ));


        ArgumentCaptor<MatchRequests> captor = ArgumentCaptor.forClass(MatchRequests.class);

        matchingService.match(myUserId);

        verify(matchRepository, times(2)).save(captor.capture());
        verify(myPreference).updateIsRerolled();

        List<MatchRequests> saved = captor.getAllValues();

        assertThat(saved)
                .extracting(MatchRequests::getMatchPercentage)
                .containsExactly(95.0, 88.0);

        assertThat(saved)
                .allSatisfy(matchRequest -> {
                    assertThat(matchRequest.getUserLow().getId()).isEqualTo(1L);
                    assertThat(matchRequest.getUserLowStatus()).isEqualTo(MatchStatus.RECOMMENDED);
                    assertThat(matchRequest.getUserHighStatus()).isEqualTo(MatchStatus.NONE);
                });

    }

    @Test
    void noCandidate(){
        Long myUserId = 1L;

        UserPreferences myPreference = mock(UserPreferences.class);


        Users me = mock(Users.class);


        when(me.getUserPreferences()).thenReturn(myPreference);

        UserDetails myDetails = mock(UserDetails.class);
        when(myDetails.getGender()).thenReturn("MALE");

        when(me.getUserDetails()).thenReturn(myDetails);

        when(myPreference.getIsMatched()).thenReturn(false);
        when(myPreference.getRerolledAt()).thenReturn(null);
        when(myPreference.getUser()).thenReturn(me);
        when(myPreference.getSmokingStatus()).thenReturn(1);
        when(myPreference.getLifestyleVector()).thenReturn(new float[]{
                0.25f, 0.0f, 0.0f, 0.75f, 0.5f, 0.33f, 0.0f, 0.5f, 1.0f
        });

        when(usersRepository.findById(1L)).thenReturn(Optional.of(me));

        when(userPreferencesRepository.findByUserIdWithUserDetails(myUserId))
                .thenReturn(Optional.of(myPreference));

        when(matchRepository.findReusableCandidatesWithSmoking(eq(myUserId), eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        when(matchRepository.findReusableCandidatesIgnoringSmoking(eq(myUserId), any(Pageable.class)))
                .thenReturn(List.of());



        when(userPreferencesRepository.findNewRecommendationCandidates(
                eq(myUserId),
                eq("MALE"),
                eq(1),
                anyString(),
                eq(3)
        )).thenReturn(List.of(
        ));

        when(userPreferencesRepository.findNewRecommendationCandidatesIgnoringSmoking(
                eq(myUserId),
                eq("MALE"),
                eq(List.of(-1L)),
                anyString(),
                eq(3)
        )).thenReturn(List.of(
        ));


        ArgumentCaptor<MatchRequests> captor = ArgumentCaptor.forClass(MatchRequests.class);

        assertThatThrownBy(() -> matchingService.match(myUserId))
                .isInstanceOf(BusinessException.class);

        verify(matchRepository,never()).save(any());
        verify(myPreference, never()).updateIsRerolled();
    }


}

