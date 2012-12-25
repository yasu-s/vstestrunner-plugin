package org.jenkinsci.plugins.vstest_runner;

import java.io.IOException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;
import hudson.model.EnvironmentSpecific;

/**
 *
 * @author Yasuyuki Saito
 */
public class VsTestInstallation extends ToolInstallation implements NodeSpecific<VsTestInstallation>, EnvironmentSpecific<VsTestInstallation> {

	/** */
	private transient String pathToVsTest;

	@DataBoundConstructor
	public VsTestInstallation(String name, String home) {
		super(name, home, null);
	}

	/**
	 *
	 */
	public VsTestInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
		return new VsTestInstallation(getName(), translateFor(node, log));
	}

	/**
	 *
	 * @param environment
	 * @return
	 */
	public VsTestInstallation forEnvironment(EnvVars environment) {
		return new VsTestInstallation(getName(), environment.expand(getHome()));
	}

	/**
	 * Used for backward compatibility
	 *
	 * @return the new object, an instance of MsBuildInstallation
	 */
	protected Object readResolve() {
		if (this.pathToVsTest != null) {
			return new VsTestInstallation(this.getName(), this.pathToVsTest);
		}
		return this;
	}

	/**
	 *
	 * @author Yasuyuki Saito
	 */
	@Extension
	public static class DescriptorImpl extends ToolDescriptor<VsTestInstallation> {

		public String getDisplayName() {
			return "VsTest";
		}

		@Override
		public VsTestInstallation[] getInstallations() {
			return Hudson.getInstance().getDescriptorByType(VsTestBuilder.DescriptorImpl.class).getInstallations();
		}

		@Override
		public void setInstallations(VsTestInstallation... installations) {
			Hudson.getInstance().getDescriptorByType(VsTestBuilder.DescriptorImpl.class).setInstallations(installations);
		}

    }
}
