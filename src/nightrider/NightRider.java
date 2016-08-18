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
	private int scanInterval;
	private double lastVelocity;
	private long lastTurtling;
	private Radar radar;

	private Gun gun;

	public class Radar {
		SortedMap<Double, ScannedRobotEvent> opponents = new TreeMap<Double, ScannedRobotEvent>();
		private ScannedRobotEvent lockedOpponent = null;
		private boolean fullScan = false;

		public void scanLock() {
			if (getTime() % scanInterval == 0) {
				startFullScan();
			}
			if (lockedOpponent != null && (getTime() - lockedOpponent.getTime()) > 1) {
				startFullScan();
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
			setTurnRadarLeft(360.0);
			opponents.clear();
			lockedOpponent = null;
			fullScan = true;
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
			if (lockedOpponent != null) 
				return lockedOpponent.getDistance();
			else 
				return getBattleFieldHeight();
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
			if (lockedOpponent != null) 
				return lockedOpponent.getName() == name;
			else 
				return false;
		}
	}

	public class Gun {

		private double gunHeading = getGunHeadingRadians();
		private double bulletPower = 1.0;
		private int shots = 0;

		public void aimOnLock() {
			if (radar.isLocked()) {
				gunHeading = Utils.normalRelativeAngle(radar.getOpponentBearing() - getGunHeadingRadians());
				setTurnGunRightRadians(gunHeading);
			}
		}

		public void aimPredictive() {
			gunHeading = Utils.normalRelativeAngle(radar.getOpponentBearing() - getGunHeadingRadians()
					+ Math.asin(radar.getOpponentLateralVelocity() / (20 - 3 * bulletPower)));
			setTurnGunRightRadians(gunHeading);
		}

		public double getHeading() {
			return gunHeading;
		}

		public void setPower(double power) {
			bulletPower = power;
		}

		public void fire() {
			setFire(bulletPower);
			shots++;
		}

		public int shotCount() {
			return shots;
		}
	}

	public void run() {
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);
		skinBot();

		radar = new Radar();
		gun = new Gun();

		scanInterval = 60;
		setAhead(10000);

		while (true) {
			radar.scanLock();
			gun.aimOnLock();
			if (radar.isLocked()) {
				if (radar.getLockedDistance() < 100.0) {
					scanInterval = 60;
					gun.setPower(5.0);
					setTurnRightRadians(0.5 * Math.PI + Math.sin(radar.getLockedDistance()));
					// setTurnRightRadians(Utils.normalRelativeAngle(0.5*Math.PI
					// - getHeadingRadians()));
					setAhead(direction * 80);
				} else {
					scanInterval = 40;
					setAhead(direction * radar.getLockedDistance() / 2);
					gun.setPower((getBattleFieldWidth() - radar.getLockedDistance()) / getBattleFieldWidth() * 3.0);
					gun.aimPredictive();

					if (offensive) {
						double strafeFactor = 1.6*Math.sin(radar.getLockedDistance()/25);
						System.out.println(""+strafeFactor);
						setTurnRightRadians(Math.random()
								* Utils.normalRelativeAngle(strafeFactor +radar.getOpponentBearing() - getHeadingRadians()));
					} else {
						// FIX
						setTurnRightRadians(gun.getHeading());
					}
				}
				if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10.0) {
					gun.fire();
					if (!offensive && gun.shotCount() % 5 == 0) {
						reverseDirection();
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
						reverseDirection();
					}
					setAhead(direction * 80);
					lastTurtling = getTime();
				}
				lastVelocity = getVelocity();
			}
			execute();
		}
	}

	private void skinBot() {
		setBodyColor(Color.black);
		setGunColor(Color.black);
		setRadarColor(Color.black);
		setBulletColor(Color.red);
		setScanColor(Color.red);
	}

	private void reverseDirection() {
		direction = -direction;
	}

	@Override
	public void onScannedRobot(ScannedRobotEvent e) {
		radar.processEvent(e);
	}

	@Override
	public void onBulletHit(BulletHitEvent e) {
		if (e.getEnergy() == 0.0 && radar.identifyLock(e.getName())) {
			radar.startFullScan();
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

	private void driveInReverse(double heading) {
		reverseDirection();
		setAhead(direction * 80);
		setTurnLeft(heading);
	}
}