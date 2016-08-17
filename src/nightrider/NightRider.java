package nightrider;

import java.awt.Color;
import java.util.SortedMap;
import java.util.TreeMap;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class NightRider extends AdvancedRobot {

	private boolean offensive = true;

	private int direction = 1;
	private int shots = 0;
	private int scanInterval;
	private double lastVelocity;
	private long lastTurtling;
	private Radar radar;

	public class Radar {
		SortedMap<Double, ScannedRobotEvent> opponents = new TreeMap<Double, ScannedRobotEvent>();
		private ScannedRobotEvent lockedOpponent = null;
		private boolean fullScan = false;
		private boolean startFullScan = true;

		public void scanLock() {
			if (getTime() % scanInterval == 0) {
				startFullScan();
			}
			if (lockedOpponent != null && (getTime() - lockedOpponent.getTime()) > 1) {
				startFullScan();
			}

			if (startFullScan) {
				setTurnRadarLeft(360.0);
				opponents.clear();
				lockedOpponent = null;
				fullScan = true;
				startFullScan = false;
			}
			if (fullScan && getRadarTurnRemaining() == 0) {
				fullScan = false;
				if (opponents.isEmpty()) {
					startFullScan();
					lockedOpponent = null;
				} else {
					lockedOpponent = opponents.get(opponents.firstKey());
				}
			}
			if (isLocked()) {
				setTurnRadarRightRadians(
						Utils.normalRelativeAngle(getOpponentBearing() - getRadarHeadingRadians()) * 2.0);
			}
		}

		public void startFullScan() {
			startFullScan = true;
		}

		public double getOpponentLateralVelocity() {
			return lockedOpponent.getVelocity() * Math.sin(lockedOpponent.getHeadingRadians() - getOpponentBearing());
		}

		public double getOpponentBearing() {
			return lockedOpponent.getBearingRadians() + getHeadingRadians();
		}

		public boolean isLocked() {
			return !fullScan && lockedOpponent != null;
		}

		public double getLockedDistance() {
			return lockedOpponent.getDistance();
		}

		public double getLockedEnergy() {
			return lockedOpponent.getEnergy();
		}

		public void processEvent(ScannedRobotEvent e) {
			if (fullScan) {
				opponents.put(e.getDistance(), e);
			} else {
				if (e.getName() == lockedOpponent.getName()) {
					lockedOpponent = e;
				}
			}
		}

		public boolean identifyLock(String name) {
			return lockedOpponent.getName() == name;
		}
	}

	public void run() {
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);
		setBodyColor(Color.black);
		setGunColor(Color.black);
		setRadarColor(Color.black);
		setBulletColor(Color.red);
		setScanColor(Color.red);

		radar = new Radar();
		double bulletPower = 1.0;

		scanInterval = 60;
		setAhead(10000);

		while (true) {
			radar.scanLock();

			if (radar.isLocked()) {
				setTurnGunRightRadians(Utils.normalRelativeAngle(radar.getOpponentBearing() - getGunHeadingRadians()));
			}

			if (radar.isLocked()) {
				if (radar.getLockedDistance() < 100.0) {
					scanInterval = 60;
					bulletPower = 5.0;
					setTurnRightRadians(0.5 * Math.PI + Math.sin(getDistanceRemaining()));
					// setTurnRightRadians(Utils.normalRelativeAngle(0.5*Math.PI
					// - getHeadingRadians()));
					setAhead(direction * 80);
					// setMaxVelocity(4.0);
				} else {
					scanInterval = 40;
					// setMaxVelocity(8.0);
					setAhead(direction * radar.getLockedDistance() / 2);
					bulletPower = (getBattleFieldWidth() - radar.getLockedDistance()) / getBattleFieldWidth() * 3.0;
					double angle = Utils.normalRelativeAngle(radar.getOpponentBearing() - getGunHeadingRadians()
							+ Math.asin(radar.getOpponentLateralVelocity() / (20 - 3 * bulletPower)));
					setTurnGunRightRadians(angle);

					if (offensive) {
						setTurnRightRadians(Math.sin(getDistanceRemaining()) + Math.random()
								* Utils.normalRelativeAngle(radar.getOpponentBearing() - getHeadingRadians()));
					} else {
						setTurnRightRadians(angle);
					}
				}
				if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10.0) {
					setFire(bulletPower);
					shots++;
					if (!offensive && shots % 5 == 0) {
						direction = -direction;
					}
				}

				if (offensive && getOthers() == 1 && getEnergy() < radar.getLockedEnergy()) {
					System.out.println("Defensive!");
					offensive = false;
				}

				// take care off if velocity is 0 for several turns then change
				// direction
				if (getVelocity() == 0.0 && lastVelocity == 0.0) {
					if (lastTurtling == getTime() - 1) {
						direction = -direction;
					}
					setAhead(direction * 80);
					lastTurtling = getTime();
				}
				lastVelocity = getVelocity();
			}
			execute();
		}
	}

	@Override
	public void onScannedRobot(ScannedRobotEvent e) {
		radar.processEvent(e);
	}

	@Override
	public void onBulletHit(BulletHitEvent e) {
		if (radar.isLocked() && radar.identifyLock(e.getName())) {
			if (e.getEnergy() == 0.0) {
				radar.startFullScan();
			}
		}
	}

	@Override
	public void onHitWall(HitWallEvent event) {
		driveInReverse(event.getBearing());
	}

	@Override
	public void onHitRobot(HitRobotEvent event) {
		driveInReverse(event.getBearing());
		if (radar.getLockedDistance() > 50) {
			radar.startFullScan();
		}
	}

	private void driveInReverse(double bearing) {
		direction = -direction;
		setAhead(direction * 80);
		setTurnLeft(bearing);
	}
}