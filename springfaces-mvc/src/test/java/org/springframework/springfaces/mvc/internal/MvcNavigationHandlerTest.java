package org.springframework.springfaces.mvc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import javax.faces.application.Application;
import javax.faces.application.ConfigurableNavigationHandler;
import javax.faces.application.NavigationCase;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.ActionListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.springfaces.mvc.FacesContextSetter;
import org.springframework.springfaces.mvc.SpringFacesContextSetter;
import org.springframework.springfaces.mvc.context.SpringFacesContext;
import org.springframework.springfaces.mvc.navigation.NavigationContext;
import org.springframework.springfaces.mvc.navigation.NavigationOutcome;
import org.springframework.springfaces.mvc.navigation.NavigationOutcomeResolver;

/**
 * Tests for {@link MvcNavigationHandler}.
 * 
 * @author Phillip Webb
 */
@RunWith(MockitoJUnitRunner.class)
public class MvcNavigationHandlerTest {

	private MvcNavigationHandler navigationHandler;

	@Mock
	private ConfigurableNavigationHandler delegate;

	@Mock
	private NavigationOutcomeResolver navigationOutcomeResolver;

	@Mock
	private FacesContext context;

	@Mock
	private Application application;

	@Mock
	private ViewHandler viewHandler;

	private String fromAction = "fromAction";

	private String outcome = "outcome";

	@Mock
	private SpringFacesContext springFacesContext;

	@Mock
	private NavigationOutcomeViewRegistry navigationOutcomeViewRegistry;

	@Mock
	private Object handler;

	@Captor
	private ArgumentCaptor<NavigationContext> navigationContext;

	private NavigationOutcome navigationOutcome = new NavigationOutcome("destination");

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setup() {
		FacesContextSetter.setCurrentInstance(context);
		navigationHandler = new MvcNavigationHandler(delegate, navigationOutcomeResolver);
		navigationHandler.setNavigationOutcomeViewRegistry(navigationOutcomeViewRegistry);
		given(navigationOutcomeViewRegistry.put(any(FacesContext.class), any(NavigationOutcome.class))).willReturn(
				"viewId");
		given(springFacesContext.getHandler()).willReturn(handler);
		given(context.getApplication()).willReturn(application);
		given(application.getViewHandler()).willReturn(viewHandler);
		Map<Object, Object> attributes = new HashMap<Object, Object>();
		given(context.getAttributes()).willReturn(attributes);
	}

	@After
	public void cleanup() {
		SpringFacesContextSetter.setCurrentInstance(null);
		FacesContextSetter.setCurrentInstance(null);
	}

	private void handleOutcome() {
		given(navigationOutcomeResolver.canResolve(navigationContext.capture())).willReturn(true);
		given(navigationOutcomeResolver.resolve(any(NavigationContext.class))).willReturn(navigationOutcome);
	}

	@Test
	public void shouldDelegateGetNavigationCaseWithoutSpringFacesContext() throws Exception {
		navigationHandler.getNavigationCase(context, fromAction, outcome);
		verify(delegate).getNavigationCase(context, fromAction, outcome);
	}

	@Test
	public void shouldDelegateGetNavigationCaseWhenNoResolve() throws Exception {
		SpringFacesContextSetter.setCurrentInstance(springFacesContext);
		navigationHandler.getNavigationCase(context, fromAction, outcome);
		verify(navigationOutcomeResolver).canResolve(navigationContext.capture());
		verify(delegate).getNavigationCase(context, fromAction, outcome);
	}

	@Test
	public void shouldNeedNavigationOutcomeForGetNavigationCase() throws Exception {
		SpringFacesContextSetter.setCurrentInstance(springFacesContext);
		given(navigationOutcomeResolver.canResolve(navigationContext.capture())).willReturn(true);
		thrown.equals(IllegalStateException.class);
		thrown.expectMessage("Unable to resolve required navigation outcome 'outcome'");
		navigationHandler.getNavigationCase(context, fromAction, outcome);
	}

	@Test
	public void shouldGetNavigationCase() throws Exception {
		SpringFacesContextSetter.setCurrentInstance(springFacesContext);
		handleOutcome();
		NavigationCase navigationCase = navigationHandler.getNavigationCase(context, fromAction, outcome);
		assertNotNull(navigationCase);
		verify(navigationOutcomeViewRegistry).put(context, navigationOutcome);
		NavigationContext navigationContext = this.navigationContext.getValue();
		assertEquals(outcome, navigationContext.getOutcome());
		assertEquals(fromAction, navigationContext.getFromAction());
		assertTrue(navigationContext.isPreemptive());
		assertNull(navigationContext.getActionEvent());
		assertSame(handler, navigationContext.getHandler());
	}

	@Test
	public void shouldDelegateHandleNavigationWithoutSpringFacesContext() throws Exception {
		navigationHandler.handleNavigation(context, fromAction, outcome);
		verify(delegate).handleNavigation(context, fromAction, outcome);
	}

	@Test
	public void shouldDelegateHandleNavigationWhenCantResolve() throws Exception {
		SpringFacesContextSetter.setCurrentInstance(springFacesContext);
		navigationHandler.handleNavigation(context, fromAction, outcome);
		verify(navigationOutcomeResolver).canResolve(navigationContext.capture());
		verify(delegate).handleNavigation(context, fromAction, outcome);
	}

	@Test
	public void shouldDelegateHandleNavigationWhenResolvesNull() throws Exception {
		SpringFacesContextSetter.setCurrentInstance(springFacesContext);
		given(navigationOutcomeResolver.canResolve(navigationContext.capture())).willReturn(true);
		navigationHandler.handleNavigation(context, fromAction, outcome);
		verify(navigationOutcomeResolver).canResolve(navigationContext.capture());
		verify(delegate).handleNavigation(context, fromAction, outcome);
	}

	@Test
	public void shouldHandleNavigation() throws Exception {
		SpringFacesContextSetter.setCurrentInstance(springFacesContext);
		UIViewRoot viewRoot = mock(UIViewRoot.class);
		ActionEvent actionEvent = mock(ActionEvent.class);
		handleOutcome();
		given(viewHandler.createView(context, "viewId")).willReturn(viewRoot);

		// Simulate the action event listener
		MvcNavigationActionListener actionLister = new MvcNavigationActionListener(mock(ActionListener.class));
		actionLister.processAction(actionEvent);

		navigationHandler.handleNavigation(context, fromAction, outcome);

		verify(navigationOutcomeViewRegistry).put(context, navigationOutcome);
		verify(context).setViewRoot(viewRoot);
		NavigationContext navigationContext = this.navigationContext.getValue();
		assertEquals(outcome, navigationContext.getOutcome());
		assertEquals(fromAction, navigationContext.getFromAction());
		assertFalse(navigationContext.isPreemptive());
		assertSame(actionEvent, navigationContext.getActionEvent());
		assertSame(handler, navigationContext.getHandler());
	}

}