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

	SortedMap<Double, ScannedRobotEvent> opponents = new TreeMap<Double, ScannedRobotEvent>();
	private ScannedRobotEvent nearestOpponent = null;
	private boolean fullScan = false;
	private boolean startFullScan = false;
	private boolean offensive = true;

	private int direction = 1;
	private int shots = 0;
	private int scanInterval;

	public void run() {
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);
		setBodyColor(Color.black);
		setGunColor(Color.black);
		setRadarColor(Color.black);
		setBulletColor(Color.red);
		setScanColor(Color.red);

		double absBearing = 0.0;
		double radarTurn = 0.0;
		double latVel = 0.0;
		double bulletPower = 1.0;

		scanInterval = 60;
		setAhead(10000);
		startFullScan = true;

		while (true) {
			if (getTime() % scanInterval == 0) {
				startFullScan = true;
			}
			if (nearestOpponent != null && (getTime() - nearestOpponent.getTime()) > 1) {
				startFullScan = true;
			}

			if (startFullScan) {
				setTurnRadarLeft(360.0);
				opponents.clear();
				nearestOpponent = null;
				fullScan = true;
				startFullScan = false;
			}
			if (fullScan && getRadarTurnRemaining() == 0) {
				fullScan = false;
				if (opponents.isEmpty()) {
					startFullScan = true;
					nearestOpponent = null;
				} else {
					nearestOpponent = opponents.get(opponents.firstKey());
				}
			}

			if (!fullScan && nearestOpponent != null) {
				absBearing = nearestOpponent.getBearingRadians() + getHeadingRadians();
				latVel = nearestOpponent.getVelocity() * Math.sin(nearestOpponent.getHeadingRadians() - absBearing);
				radarTurn = absBearing - getRadarHeadingRadians();
				setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn) * 2.0);
				setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians()));
			}

			if (getGunHeat() == 0 && nearestOpponent != null) {
				if (nearestOpponent.getDistance() < 100.0) {
					scanInterval = 60;
					bulletPower = 5.0;
					setTurnRightRadians(0.5 * Math.PI);
//					 setTurnRightRadians(Utils.normalRelativeAngle(0.5*Math.PI
//					 - getHeadingRadians()));
					setAhead(direction * 100);
//					setMaxVelocity(4.0);
				} else {
					scanInterval = 40;
//					setMaxVelocity(8.0);
					setAhead(direction * nearestOpponent.getDistance() / 2);
					bulletPower = (getBattleFieldWidth() - nearestOpponent.getDistance()) / getBattleFieldWidth() * 5.0;
					double angle = Utils.normalRelativeAngle(
							absBearing - getGunHeadingRadians() + Math.asin(latVel / (20 - 3 * bulletPower)));
					setTurnGunRightRadians(angle);

					if (offensive) {
						setTurnRightRadians(
								Math.random() * Utils.normalRelativeAngle(absBearing - getHeadingRadians()));
					} else {
						setTurnGunRightRadians(angle);
					}
				}
				if (Math.abs(getGunTurnRemaining()) < 10.0) {
					setFire(bulletPower);
					shots++;
					if (!offensive && shots % 5 == 0) {
						direction = -direction;
					}
				}
				
				if (offensive && getOthers() == 1 && getEnergy() < nearestOpponent.getEnergy() ) {
					System.out.println("Defensive!");
					offensive = false;
				}
				// take care off if vel is 0 for several turns then change direction
			}
			execute();
		}
	}

	@Override
	public void onScannedRobot(ScannedRobotEvent e) {
		if (fullScan) {
			opponents.put(e.getDistance(), e);
		} else {
			if (e.getName() == nearestOpponent.getName()) {
				nearestOpponent = e;
			}
		}
	}

	@Override
	public void onBulletHit(BulletHitEvent e) {
		if (nearestOpponent != null && e.getName() == nearestOpponent.getName()) {
			if (e.getEnergy() == 0.0) {
				startFullScan = true;
			}
		}
	}

	@Override
	public void onHitWall(HitWallEvent event) {
		setBack(direction * 80);
		setTurnLeft(event.getBearing());
		// change direction? & reuse remaining distance & turning?
	}

	@Override
	public void onHitRobot(HitRobotEvent event) {
		setBack(direction * 80);
		setTurnLeft(event.getBearing());
		// change direction? & reuse remaining distance & turning?
	}

}
